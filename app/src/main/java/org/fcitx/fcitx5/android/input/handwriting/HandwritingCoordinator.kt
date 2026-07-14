/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.Context
import android.os.SystemClock
import android.os.Trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class HandwritingCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val commitText: (String) -> Unit,
    private val refreshCandidates: () -> Unit,
    private val hideCandidates: () -> Unit
) {
    private enum class Backend { OFFLINE, ENHANCED }

    private val offlineRecognizer = OfflineHanziRecognizer(context.applicationContext)
    private val enhancedRecognizer = MlKitHandwritingRecognizer()

    private var active = false
    private var strokes: List<HandwritingStroke> = emptyList()
    private var candidates: List<String> = emptyList()
    private var selectedIndex = 0
    private var revision = 0L
    private var generation = 0L
    private var activeBackend: Backend? = null
    private var offlineReady = false
    private var enhancedReady = false
    private var enhancedDownloadFailed = false
    private var modelState = HandwritingModelState.PREPARING_OFFLINE
    private var recognizing = false
    private var noMatch = false
    private var recognitionJob: Job? = null
    private var offlineWarmupJob: Job? = null
    private var enhancedModelJob: Job? = null
    private var stateListener: ((HandwritingViewState) -> Unit)? = null

    val isActive: Boolean
        get() = active

    val hasStrokes: Boolean
        get() = strokes.isNotEmpty()

    fun begin() {
        if (active) return
        active = true
        clearCharacter(publish = false)
        publishState()
        warmupRecognizers()
        hideCandidates()
    }

    fun end() {
        if (!active) return
        active = false
        generation++
        recognitionJob?.cancel()
        clearCharacter(publish = false)
        stateListener = null
        hideCandidates()
    }

    fun setStateListener(listener: ((HandwritingViewState) -> Unit)?) {
        stateListener = listener
        if (listener != null && active) publishState()
    }

    fun addStroke(stroke: HandwritingStroke) {
        if (!active || stroke.points.isEmpty()) return
        if (strokes.isEmpty()) {
            // A character keeps one recognizer for its entire lifetime. Switching to a model that
            // finishes downloading mid-character makes candidates jump for no user action.
            activeBackend = if (enhancedReady) Backend.ENHANCED else Backend.OFFLINE
        }
        strokes = strokes + HandwritingStroke(stroke.points.toList())
        recognizing = true
        noMatch = false
        revision++
        publishState()
        scheduleRecognition()
    }

    fun undoStroke(): Boolean {
        if (!active || strokes.isEmpty()) return false
        strokes = strokes.dropLast(1)
        generation++
        recognitionJob?.cancel()
        recognizing = strokes.isNotEmpty()
        noMatch = false
        revision++
        if (strokes.isEmpty()) {
            activeBackend = null
            candidates = emptyList()
            selectedIndex = 0
            publishState()
            hideCandidates()
        } else {
            publishState()
            scheduleRecognition()
        }
        return true
    }

    fun clear(): Boolean {
        if (!active || strokes.isEmpty() && candidates.isEmpty()) return false
        clearCharacter(publish = true)
        hideCandidates()
        return true
    }

    fun snapshot(): HandwritingUiSnapshot? = if (active) {
        HandwritingUiSnapshot(
            revision = revision,
            candidates = candidates,
            selectedIndex = if (candidates.isEmpty()) 0 else selectedIndex.coerceIn(candidates.indices)
        )
    } else {
        null
    }

    fun moveSelectionTo(index: Int): Boolean {
        if (!active || recognizing || index !in candidates.indices) return false
        if (selectedIndex == index) return true
        selectedIndex = index
        revision++
        refreshCandidates()
        return true
    }

    fun commitCandidate(index: Int? = null): Boolean {
        if (!active || recognizing) return false
        val resolvedIndex = index ?: selectedIndex
        val text = candidates.getOrNull(resolvedIndex) ?: return false
        commitText(text)
        clearCharacter(publish = true)
        hideCandidates()
        return true
    }

    fun retryEnhancedModel() {
        if (enhancedReady || enhancedModelJob?.isActive == true) return
        startEnhancedModelDownload()
    }

    fun close() {
        recognitionJob?.cancel()
        offlineWarmupJob?.cancel()
        enhancedModelJob?.cancel()
        enhancedRecognizer.close()
    }

    private fun warmupRecognizers() {
        if (!offlineReady && offlineWarmupJob?.isActive != true) {
            offlineWarmupJob = scope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        traced("Handwriting.offlineWarmup") { offlineRecognizer.warmup() }
                    }
                }.onSuccess {
                    offlineReady = true
                    updateAvailableModelState()
                    if (active && strokes.isNotEmpty() && candidates.isEmpty()) scheduleRecognition()
                }.onFailure { Timber.e(it, "Unable to prepare bundled handwriting recognizer") }
            }
        }
        if (enhancedModelJob?.isActive != true && !enhancedReady) {
            startEnhancedModelDownload()
        }
    }

    private fun startEnhancedModelDownload() {
        enhancedDownloadFailed = false
        modelState = if (offlineReady) {
            HandwritingModelState.DOWNLOADING_ENHANCED
        } else {
            HandwritingModelState.PREPARING_OFFLINE
        }
        publishState()
        enhancedModelJob = scope.launch {
            runCatching {
                if (!enhancedRecognizer.isDownloaded()) enhancedRecognizer.download()
                // Model initialization can take over a second on slower phones. Warming it while
                // the canvas is idle keeps that cost off the first enhanced recognition request.
                enhancedRecognizer.warmup()
            }.onSuccess {
                enhancedReady = true
                modelState = HandwritingModelState.ENHANCED_READY
            }.onFailure {
                Timber.w(it, "Enhanced handwriting model download failed; bundled recognizer remains active")
                enhancedDownloadFailed = true
                modelState = if (offlineReady) {
                    HandwritingModelState.ENHANCED_DOWNLOAD_FAILED
                } else {
                    HandwritingModelState.PREPARING_OFFLINE
                }
            }
            if (active) publishState()
        }
    }

    private fun updateAvailableModelState() {
        modelState = when {
            enhancedReady -> HandwritingModelState.ENHANCED_READY
            enhancedDownloadFailed -> HandwritingModelState.ENHANCED_DOWNLOAD_FAILED
            enhancedModelJob?.isActive == true -> HandwritingModelState.DOWNLOADING_ENHANCED
            else -> HandwritingModelState.OFFLINE_READY
        }
        if (active) publishState()
    }

    private fun scheduleRecognition() {
        val requestGeneration = ++generation
        val requestStrokes = strokes
        val requestBackend = activeBackend ?: Backend.OFFLINE
        recognitionJob?.cancel()
        recognitionJob = scope.launch {
            // The first stroke deserves immediate feedback, while later strokes are usually part
            // of the same Hanzi. A slightly longer continuation window avoids reranking and
            // relaying out the candidate bubble between naturally rapid strokes.
            delay(
                if (requestStrokes.size == 1) {
                    FirstStrokeDebounceMillis
                } else {
                    ContinuationStrokeDebounceMillis
                }
            )
            val started = SystemClock.elapsedRealtimeNanos()
            val batch = runCatching {
                when (requestBackend) {
                    Backend.ENHANCED -> {
                        val enhanced = enhancedRecognizer.recognize(requestStrokes, CandidateLimit)
                        if (enhanced.isNotEmpty()) {
                            RecognitionBatch(enhanced, Backend.ENHANCED)
                        } else {
                            RecognitionBatch(recognizeOffline(requestStrokes), Backend.OFFLINE)
                        }
                    }
                    Backend.OFFLINE -> RecognitionBatch(
                        recognizeOffline(requestStrokes),
                        Backend.OFFLINE
                    )
                }
            }.recoverCatching { error ->
                if (requestBackend != Backend.ENHANCED) throw error
                Timber.w(error, "Enhanced handwriting recognition failed; using bundled recognizer")
                RecognitionBatch(recognizeOffline(requestStrokes), Backend.OFFLINE)
            }.getOrElse {
                Timber.e(it, "Handwriting recognition failed")
                RecognitionBatch(emptyList(), requestBackend)
            }
            if (!active || requestGeneration != generation || requestStrokes != strokes) return@launch
            candidates = batch.candidates.map(HandwritingRecognition::text).distinct()
            selectedIndex = 0
            recognizing = false
            noMatch = candidates.isEmpty()
            revision++
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
            Timber.d(
                "Handwriting recognition backend=%s strokes=%d candidates=%d latency=%.2fms",
                batch.backend,
                requestStrokes.size,
                candidates.size,
                elapsedMs
            )
            publishState()
            refreshCandidates()
        }
    }

    private suspend fun recognizeOffline(
        requestStrokes: List<HandwritingStroke>
    ): List<HandwritingRecognition> = withContext(Dispatchers.Default) {
        val recognitionContext = currentCoroutineContext()
        traced("Handwriting.offlineRecognize") {
            offlineRecognizer.recognize(
                requestStrokes,
                CandidateLimit,
                cancelled = { !recognitionContext.isActive }
            )
        }
    }

    private fun clearCharacter(publish: Boolean) {
        generation++
        recognitionJob?.cancel()
        strokes = emptyList()
        candidates = emptyList()
        selectedIndex = 0
        activeBackend = null
        recognizing = false
        noMatch = false
        revision++
        if (publish && active) publishState()
    }

    private fun publishState() {
        stateListener?.invoke(
            HandwritingViewState(
                strokes = strokes,
                modelState = modelState,
                recognizing = recognizing,
                noMatch = noMatch
            )
        )
    }

    private data class RecognitionBatch(
        val candidates: List<HandwritingRecognition>,
        val backend: Backend
    )

    private inline fun <T> traced(section: String, block: () -> T): T {
        Trace.beginSection(section)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    private companion object {
        const val FirstStrokeDebounceMillis = 55L
        const val ContinuationStrokeDebounceMillis = 120L
        const val CandidateLimit = 30
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.Context
import android.os.SystemClock
import android.os.Trace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.input.t9.EnglishSuggestionEngine
import timber.log.Timber

internal class HandwritingCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val commitText: (String) -> Unit,
    private val hideExternalCandidates: () -> Unit,
    candidatePageSize: () -> Int,
    private val showPronunciationAfterCommit: () -> Boolean,
    private val lookupPronunciations: suspend (String) -> List<String>,
    private val smartEnglishEnabled: () -> Boolean,
    private val shouldLearnEnglishWords: () -> Boolean,
    englishSuggestionEngine: EnglishSuggestionEngine = EnglishSuggestionEngine.Shared,
    private val enhancedBackend: EnhancedHandwritingBackend = MlKitEnhancedHandwritingBackend()
) {
    private enum class Backend { OFFLINE, ENHANCED }

    private val offlineRecognizer = OfflineHanziRecognizer(context.applicationContext)
    private val candidateSession = HandwritingCandidateSession(candidatePageSize)
    private val englishSession = EnglishHandwritingSession(
        suggestionEngine = englishSuggestionEngine,
        candidateLimit = CandidateLimit
    )

    private var active = false
    private var language = HandwritingLanguage.CHINESE
    private var strokes: List<HandwritingStroke> = emptyList()
    private var writingArea: HandwritingWritingArea? = null
    private var generation = 0L
    private var activeBackend: Backend? = null
    private var candidateSource = HandwritingCandidateSource.NONE
    private var offlineReady = false
    private val enhancedReady = mutableSetOf<HandwritingLanguage>()
    private val enhancedModelMissing = mutableSetOf<HandwritingLanguage>()
    private var modelState = HandwritingModelState.PREPARING_OFFLINE
    private var recognizing = false
    private var noMatch = false
    private var pronunciation: HandwritingPronunciationFeedback? = null
    private var pronunciationGeneration = 0L
    private var recognitionJob: Job? = null
    private var pronunciationJob: Job? = null
    private var offlineWarmupJob: Job? = null
    private var enhancedModelJob: Job? = null
    private var stateListener: ((HandwritingViewState) -> Unit)? = null

    val isActive: Boolean
        get() = active

    val hasStrokes: Boolean
        get() = strokes.isNotEmpty()

    val hasCandidates: Boolean
        get() = candidateSession.hasCandidates

    val hasPendingCharacter: Boolean
        get() = strokes.isNotEmpty() || candidateSource == HandwritingCandidateSource.RECOGNITION

    fun begin(initialLanguage: HandwritingLanguage, editorPreContext: String) {
        if (active) return
        active = true
        language = initialLanguage
        englishSession.begin(editorPreContext)
        invalidatePronunciationFeedback()
        clearCharacter(publish = false)
        updateAvailableModelState(publish = false)
        publishState()
        warmupRecognizers()
        hideExternalCandidates()
    }

    fun end() {
        if (!active) return
        active = false
        generation++
        recognitionJob?.cancel()
        enhancedModelJob?.cancel()
        invalidatePronunciationFeedback()
        clearCharacter(publish = false)
        englishSession.clear()
        stateListener = null
        hideExternalCandidates()
    }

    fun setStateListener(listener: ((HandwritingViewState) -> Unit)?) {
        stateListener = listener
        if (listener != null && active) publishState()
    }

    fun updateWritingArea(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val next = HandwritingWritingArea(width.toFloat(), height.toFloat())
        if (writingArea != next) writingArea = next
    }

    fun switchLanguage(editorPreContext: String): HandwritingLanguage {
        if (!active) return language
        enhancedModelJob?.cancel()
        enhancedModelJob = null
        clearCharacter(publish = false)
        invalidatePronunciationFeedback()
        language = when (language) {
            HandwritingLanguage.CHINESE -> HandwritingLanguage.ENGLISH
            HandwritingLanguage.ENGLISH -> HandwritingLanguage.CHINESE
        }
        // Re-read the editor at the explicit language boundary. This prevents English pair
        // prediction from jumping across intervening Chinese text without polling on stroke input.
        englishSession.begin(editorPreContext)
        updateAvailableModelState(publish = false)
        publishState()
        warmupRecognizers()
        return language
    }

    fun beginStroke() {
        if (!active) return
        if (strokes.isEmpty() && activeBackend == null) {
            // Chinese can take the bundled floor immediately. English deliberately keeps the ML
            // model as its only backend rather than presenting low-quality Latin guesses.
            activeBackend = when (language) {
                HandwritingLanguage.CHINESE -> {
                    if (language in enhancedReady) Backend.ENHANCED else Backend.OFFLINE
                }
                HandwritingLanguage.ENGLISH -> Backend.ENHANCED
            }
        }
        if (language !in enhancedReady) {
            // Native preparation never competes with an active pointer stream. English may prepare
            // after the word boundary, when no bundled recognizer can answer the request.
            enhancedModelJob?.cancel()
            enhancedModelJob = null
        }
        generation++
        recognitionJob?.cancel()
        val candidatesChanged = invalidatePublishedCandidates()
        val visibleStateChanged = noMatch || pronunciation != null || candidatesChanged
        invalidatePronunciationFeedback()
        noMatch = false
        if (visibleStateChanged) publishState()
    }

    fun addStroke(stroke: HandwritingStroke) {
        if (!active || stroke.points.isEmpty()) return
        invalidatePronunciationFeedback()
        strokes = strokes + stroke
        invalidatePublishedCandidates()
        recognizing = true
        noMatch = false
        publishState()
        scheduleRecognition()
    }

    fun undoStroke(): Boolean {
        if (!active || strokes.isEmpty()) return false
        strokes = strokes.dropLast(1)
        generation++
        recognitionJob?.cancel()
        invalidatePublishedCandidates()
        recognizing = strokes.isNotEmpty()
        noMatch = false
        if (strokes.isEmpty()) {
            activeBackend = null
            publishState()
            scheduleEnhancedPreparation()
        } else {
            publishState()
            scheduleRecognition()
        }
        return true
    }

    private fun clear(): Boolean {
        if (!active || strokes.isEmpty() && !candidateSession.hasCandidates && pronunciation == null) {
            return false
        }
        invalidatePronunciationFeedback()
        clearCharacter(publish = true)
        scheduleEnhancedPreparation()
        return true
    }

    /** Returns true only when the backspace was consumed by an unfinished handwritten character. */
    fun consumeBackspaceBeforeEditor(): Boolean {
        if (!active) return false
        if (hasPendingCharacter) {
            clear()
            return true
        }
        if (candidateSource == HandwritingCandidateSource.PREDICTION) {
            candidateSession.clear()
            candidateSource = HandwritingCandidateSource.NONE
            publishState()
        }
        return false
    }

    fun moveSelectionBy(delta: Int): Boolean {
        if (!active || recognizing || !candidateSession.move(delta)) return false
        publishState()
        return true
    }

    fun offsetCandidatePage(delta: Int): Boolean {
        if (!active || recognizing || !candidateSession.offsetPage(delta)) return false
        publishState()
        return true
    }

    fun commitCurrentPageShortcut(shortcutIndex: Int): Boolean {
        val originalIndex = candidateSession.originalIndexForShortcut(shortcutIndex) ?: return false
        return commitCandidate(originalIndex)
    }

    fun commitCandidate(index: Int? = null): Boolean =
        commitSelectedCandidate(index = index, explicitSelection = true, publish = true)

    fun commitLiteral(text: String) {
        prepareBoundary(publish = false)
        if (language == HandwritingLanguage.ENGLISH) englishSession.commitLiteral(text)
        commitText(text)
        if (active) publishState()
        scheduleEnhancedPreparation()
    }

    fun prepareForAuxiliaryInput() {
        prepareBoundary(publish = true)
        scheduleEnhancedPreparation()
    }

    fun prepareForReturn() {
        prepareBoundary(publish = false)
        englishSession.breakContext()
        if (active) publishState()
        scheduleEnhancedPreparation()
    }

    fun close() {
        recognitionJob?.cancel()
        invalidatePronunciationFeedback()
        offlineWarmupJob?.cancel()
        enhancedModelJob?.cancel()
        enhancedBackend.close()
    }

    private fun prepareBoundary(publish: Boolean) {
        val committed = if (candidateSource == HandwritingCandidateSource.RECOGNITION) {
            commitSelectedCandidate(explicitSelection = false, publish = false)
        } else {
            false
        }
        if (!committed && hasPendingCharacter) clearCharacter(publish = false)
        if (candidateSource == HandwritingCandidateSource.PREDICTION) {
            candidateSession.clear()
            candidateSource = HandwritingCandidateSource.NONE
        }
        if (publish && active) publishState()
    }

    private fun commitSelectedCandidate(
        index: Int? = null,
        explicitSelection: Boolean,
        publish: Boolean
    ): Boolean {
        if (!active || recognizing || candidateSource == HandwritingCandidateSource.NONE) return false
        val resolvedIndex = index ?: candidateSession.selectedOriginalIndex ?: return false
        val text = candidateSession.candidateAt(resolvedIndex) ?: return false
        val sourceAtCommit = candidateSource
        val languageAtCommit = language
        val englishWord = languageAtCommit == HandwritingLanguage.ENGLISH &&
            HandwritingRecognitionTextPolicy.isEnglishWord(text)
        val emittedText = if (englishWord && explicitSelection) "$text " else text

        invalidatePronunciationFeedback()
        commitText(emittedText)
        clearCharacter(publish = false)

        if (languageAtCommit == HandwritingLanguage.ENGLISH) {
            if (englishWord) {
                val predictions = englishSession.commitWord(
                    rawWord = text,
                    emittedText = emittedText,
                    suggestionsEnabled = smartEnglishEnabled(),
                    shouldLearn = shouldLearnEnglishWords(),
                    continuePrediction = explicitSelection && smartEnglishEnabled(),
                    learnWord = sourceAtCommit == HandwritingCandidateSource.RECOGNITION
                )
                if (predictions.isNotEmpty()) {
                    candidateSession.replace(predictions)
                    candidateSource = HandwritingCandidateSource.PREDICTION
                }
            } else {
                englishSession.commitLiteral(emittedText)
            }
        } else {
            publishPronunciation(text)
        }

        if (publish && active) publishState()
        scheduleEnhancedPreparation()
        return true
    }

    private fun warmupRecognizers() {
        if (language == HandwritingLanguage.CHINESE) warmupOfflineRecognizer()
        scheduleEnhancedPreparation(recheckMissingModel = true)
    }

    private fun warmupOfflineRecognizer() {
        if (!offlineReady && offlineWarmupJob?.isActive != true) {
            offlineWarmupJob = scope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        traced("Handwriting.offlineWarmup") { offlineRecognizer.warmup() }
                    }
                }.onSuccess {
                    offlineReady = true
                    updateAvailableModelState()
                    if (
                        active && language == HandwritingLanguage.CHINESE && strokes.isNotEmpty() &&
                        !candidateSession.hasCandidates
                    ) {
                        scheduleRecognition()
                    }
                }.onFailure { Timber.e(it, "Unable to prepare bundled handwriting recognizer") }
            }
        }
    }

    private fun scheduleEnhancedPreparation(recheckMissingModel: Boolean = false) {
        if (!active || enhancedModelJob?.isActive == true || strokes.isNotEmpty()) return
        val requestedLanguage = language
        if (requestedLanguage in enhancedReady) return
        if (requestedLanguage in enhancedModelMissing && !recheckMissingModel) return
        if (recheckMissingModel) enhancedModelMissing -= requestedLanguage
        updateAvailableModelState()
        enhancedModelJob = scope.launch {
            try {
                delay(EnhancedWarmupDelayMillis)
                val ready = enhancedBackend.prepare(requestedLanguage)
                recordEnhancedAvailability(requestedLanguage, ready)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Timber.w(
                    error,
                    "Unable to prepare %s handwriting model",
                    requestedLanguage
                )
            }
            if (active && language == requestedLanguage) updateAvailableModelState()
        }
    }

    private fun recordEnhancedAvailability(requestedLanguage: HandwritingLanguage, ready: Boolean) {
        if (ready) {
            enhancedReady += requestedLanguage
            enhancedModelMissing -= requestedLanguage
        } else {
            enhancedReady -= requestedLanguage
            enhancedModelMissing += requestedLanguage
        }
    }

    private fun updateAvailableModelState(publish: Boolean = true) {
        val updatedState = when {
            language in enhancedReady -> HandwritingModelState.ENHANCED_READY
            language in enhancedModelMissing -> HandwritingModelState.ENHANCED_MODEL_MISSING
            language == HandwritingLanguage.ENGLISH -> HandwritingModelState.PREPARING_ENHANCED
            !offlineReady -> HandwritingModelState.PREPARING_OFFLINE
            else -> HandwritingModelState.OFFLINE_READY
        }
        if (modelState == updatedState) return
        modelState = updatedState
        if (publish && active) publishState()
    }

    private fun scheduleRecognition() {
        val requestGeneration = ++generation
        val requestStrokes = strokes
        val requestLanguage = language
        val requestBackend = activeBackend ?: Backend.OFFLINE
        val request = HandwritingRecognitionRequest(
            language = requestLanguage,
            strokes = requestStrokes,
            writingArea = writingArea,
            preContext = if (requestLanguage == HandwritingLanguage.ENGLISH) {
                englishSession.recognitionPreContext()
            } else {
                ""
            },
            limit = CandidateLimit
        )
        recognitionJob?.cancel()
        recognitionJob = scope.launch {
            delay(
                when (requestLanguage) {
                    HandwritingLanguage.CHINESE -> ChineseRecognitionIdleMillis
                    HandwritingLanguage.ENGLISH -> EnglishRecognitionIdleMillis
                }
            )
            val started = SystemClock.elapsedRealtimeNanos()
            val batch = recognize(request, requestBackend)
            if (
                !active || requestGeneration != generation || requestStrokes != strokes ||
                requestLanguage != language
            ) {
                return@launch
            }
            val rawCandidates = batch.candidates.map(HandwritingRecognition::text).distinct()
            val recognizedCandidates = if (requestLanguage == HandwritingLanguage.ENGLISH) {
                traced("Handwriting.englishRerank") {
                    englishSession.rerank(rawCandidates, smartEnglishEnabled())
                }
            } else {
                rawCandidates
            }
            candidateSession.replace(recognizedCandidates)
            candidateSource = if (recognizedCandidates.isEmpty()) {
                HandwritingCandidateSource.NONE
            } else {
                HandwritingCandidateSource.RECOGNITION
            }
            recognizing = false
            noMatch = recognizedCandidates.isEmpty() && !batch.modelMissing
            updateAvailableModelState(publish = false)
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
            Timber.d(
                "Handwriting recognition language=%s backend=%s strokes=%d candidates=%d latency=%.2fms",
                requestLanguage,
                batch.backend,
                requestStrokes.size,
                recognizedCandidates.size,
                elapsedMs
            )
            publishState()
        }
    }

    private suspend fun recognize(
        request: HandwritingRecognitionRequest,
        requestBackend: Backend
    ): RecognitionBatch = try {
        when (requestBackend) {
            Backend.OFFLINE -> RecognitionBatch(recognizeOffline(request.strokes), Backend.OFFLINE)
            Backend.ENHANCED -> {
                val ready = request.language in enhancedReady || enhancedBackend.prepare(request.language)
                recordEnhancedAvailability(request.language, ready)
                if (!ready) {
                    RecognitionBatch(emptyList(), Backend.ENHANCED, modelMissing = true)
                } else {
                    val enhanced = enhancedBackend.recognize(request)
                    if (enhanced.isNotEmpty() || request.language == HandwritingLanguage.ENGLISH) {
                        RecognitionBatch(enhanced, Backend.ENHANCED)
                    } else {
                        RecognitionBatch(recognizeOffline(request.strokes), Backend.OFFLINE)
                    }
                }
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        if (requestBackend == Backend.ENHANCED && request.language == HandwritingLanguage.CHINESE) {
            Timber.w(error, "Enhanced handwriting recognition failed; using bundled recognizer")
            try {
                RecognitionBatch(recognizeOffline(request.strokes), Backend.OFFLINE)
            } catch (fallbackError: CancellationException) {
                throw fallbackError
            } catch (fallbackError: Throwable) {
                Timber.e(fallbackError, "Bundled handwriting recognition failed")
                RecognitionBatch(emptyList(), Backend.OFFLINE)
            }
        } else {
            Timber.e(error, "Handwriting recognition failed for %s", request.language)
            RecognitionBatch(emptyList(), requestBackend)
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
        candidateSession.clear()
        candidateSource = HandwritingCandidateSource.NONE
        activeBackend = null
        recognizing = false
        noMatch = false
        if (publish && active) publishState()
    }

    private fun invalidatePublishedCandidates(): Boolean {
        if (!candidateSession.hasCandidates) return false
        candidateSession.clear()
        candidateSource = HandwritingCandidateSource.NONE
        return true
    }

    private fun publishPronunciation(character: String) {
        if (!showPronunciationAfterCommit()) return
        val requestGeneration = ++pronunciationGeneration
        pronunciationJob = scope.launch {
            val feedback = runCatching {
                HandwritingPronunciationFeedback.create(
                    character,
                    lookupPronunciations(character)
                )
            }.onFailure {
                Timber.w(it, "Unable to look up handwriting pronunciation")
            }.getOrNull()
            if (!active || requestGeneration != pronunciationGeneration || feedback == null) {
                return@launch
            }
            pronunciation = feedback
            publishState()
            delay(PronunciationDisplayMillis)
            if (active && requestGeneration == pronunciationGeneration) {
                pronunciation = null
                pronunciationJob = null
                publishState()
            }
        }
    }

    private fun invalidatePronunciationFeedback() {
        pronunciationGeneration++
        pronunciationJob?.cancel()
        pronunciationJob = null
        pronunciation = null
    }

    private fun publishState() {
        stateListener?.invoke(
            HandwritingViewState(
                language = language,
                strokes = strokes,
                candidatePage = candidateSession.snapshot(),
                candidateSource = candidateSource,
                modelState = modelState,
                recognizing = recognizing,
                noMatch = noMatch,
                pronunciation = pronunciation
            )
        )
    }

    private data class RecognitionBatch(
        val candidates: List<HandwritingRecognition>,
        val backend: Backend,
        val modelMissing: Boolean = false
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
        const val ChineseRecognitionIdleMillis = 800L
        const val EnglishRecognitionIdleMillis = 700L
        const val PronunciationDisplayMillis = 4_500L
        const val EnhancedWarmupDelayMillis = 2_000L
        const val CandidateLimit = 30
    }
}

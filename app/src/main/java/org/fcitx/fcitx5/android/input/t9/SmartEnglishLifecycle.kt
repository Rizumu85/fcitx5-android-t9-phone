/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import java.util.Locale

internal class SmartEnglishLifecycle(
    private val candidateProvider: (digits: String, limit: Int) -> List<String>,
    private val predictionProvider: (previousWords: List<String>, limit: Int) -> List<String>,
    private val learnWord: (String) -> Unit,
    private val learnPredictionPair: (String, String) -> Unit,
    private val dictionaryReady: () -> Boolean,
    private val predictionReady: () -> Boolean,
    private val dictionaryGeneration: () -> Long = { 0L },
    private val predictionGeneration: () -> Long = { 0L },
    private val candidateLimit: Int,
    noMatchText: String,
    private val isActive: () -> Boolean,
    private val shouldLearnWords: () -> Boolean,
    private val commitText: (String) -> Unit,
    private val refreshUi: () -> Unit
) {
    private enum class CaseState {
        OFF,
        SHIFT_ONCE,
        CAPS
    }

    private data class RawLookupKey(
        val digits: String,
        val dictionaryGeneration: Long,
        val dictionaryReady: Boolean
    )

    private data class PairRankCacheKey(
        val rawLookupKey: RawLookupKey,
        val previousWords: List<String>,
        val predictionGeneration: Long,
        val predictionReady: Boolean
    )

    private enum class CandidateSource {
        NONE,
        TYPED,
        PREDICTION
    }

    private data class CandidateContentKey(
        val contentRevision: Long,
        val active: Boolean,
        val dictionaryGeneration: Long,
        val predictionGeneration: Long,
        val dictionaryReady: Boolean,
        val predictionReady: Boolean,
        val caseState: CaseState
    ) {
        val stableKey: String
            get() = buildString {
                append(contentRevision).append('|')
                append(active).append('|')
                append(dictionaryGeneration).append('|')
                append(predictionGeneration).append('|')
                append(dictionaryReady).append('|')
                append(predictionReady).append('|')
                append(caseState.name)
            }
    }

    private data class CandidateContent(
        val source: CandidateSource,
        val candidates: Array<FcitxEvent.Candidate>,
        val rawTypedCandidates: List<String> = emptyList(),
        val reserveTopReadingRow: Boolean = false
    )

    private data class SnapshotKey(
        val inputRevision: Long,
        val contentKey: CandidateContentKey
    )

    private val session = SmartEnglishT9Session(noMatchText = noMatchText)
    private val predictionSession = SmartEnglishPredictionSession(
        predictionProvider = predictionProvider,
        candidateLimit = candidateLimit
    )
    private var rawLookupKey: RawLookupKey? = null
    private var rawLookupValue: List<String> = emptyList()
    private var pairRankCacheKey: PairRankCacheKey? = null
    private var pairRankCacheValue: List<String> = emptyList()
    private var candidateContentKey: CandidateContentKey? = null
    private var candidateContentValue: CandidateContent? = null
    private var snapshotKey: SnapshotKey? = null
    private var snapshotValue: SmartEnglishSnapshot? = null
    private var predictionCandidatesGeneration = Long.MIN_VALUE
    private val learningWord = StringBuilder()
    private val recentWords = ArrayDeque<String>()
    private var caseState = CaseState.OFF
    private var inputRevision = 0L
    private var contentRevision = 0L

    val hasDigits: Boolean
        get() = session.hasDigits

    val hasCandidates: Boolean
        get() = session.hasDigits || predictionSession.isVisible

    val shouldRefreshAfterWarmup: Boolean
        get() = session.hasDigits || predictionSession.hasContext

    val caseLabel: String
        get() = when (caseState) {
            CaseState.OFF -> "abc"
            CaseState.SHIFT_ONCE -> "Abc"
            CaseState.CAPS -> "ABC"
        }

    fun appendDigit(digit: Int) {
        if (digit !in 2..9) return
        predictionSession.reset()
        session.appendDigit(digit)
        advanceRevision()
        refreshUi()
    }

    fun reset() {
        session.reset()
        predictionSession.reset()
        recentWords.clear()
        predictionCandidatesGeneration = Long.MIN_VALUE
        advanceRevision()
        refreshUi()
    }

    fun snapshot(): SmartEnglishSnapshot {
        val active = isActive()
        val dictionaryIsReady = dictionaryReady()
        val predictionIsReady = predictionReady()
        // Read readiness before generation because preload publishes generation first and readiness last.
        val currentDictionaryGeneration = dictionaryGeneration()
        val currentPredictionGeneration = predictionGeneration()
        if (!session.hasDigits && predictionSession.hasContext && predictionIsReady &&
            predictionCandidatesGeneration != currentPredictionGeneration
        ) {
            predictionSession.refreshCandidates()
            predictionCandidatesGeneration = currentPredictionGeneration
        }
        val contentKey = CandidateContentKey(
            contentRevision = contentRevision,
            active = active,
            dictionaryGeneration = currentDictionaryGeneration,
            predictionGeneration = currentPredictionGeneration,
            dictionaryReady = dictionaryIsReady,
            predictionReady = predictionIsReady,
            caseState = caseState
        )
        val key = SnapshotKey(inputRevision = inputRevision, contentKey = contentKey)
        if (key == snapshotKey) return requireNotNull(snapshotValue)
        val content = candidateContent(contentKey)
        val cursor = if (content.candidates.isEmpty()) {
            -1
        } else {
            when (content.source) {
                CandidateSource.NONE -> -1
                CandidateSource.TYPED -> session.cursor
                CandidateSource.PREDICTION -> predictionSession.cursor
            }.coerceIn(content.candidates.indices)
        }
        val previewText = when (content.source) {
            CandidateSource.NONE -> null
            CandidateSource.TYPED -> applyCaseToWord(
                session.inputPreviewText(content.rawTypedCandidates)
            )
            CandidateSource.PREDICTION -> content.candidates.getOrNull(cursor)?.text
        }
        val stableContentKey = contentKey.stableKey
        return SmartEnglishSnapshot(
            publicationKey = "$stableContentKey|$inputRevision",
            contentKey = stableContentKey,
            inputRevision = inputRevision,
            dictionaryGeneration = currentDictionaryGeneration,
            predictionGeneration = currentPredictionGeneration,
            paged = if (content.candidates.isNotEmpty()) paged(content.candidates, cursor) else null,
            previewText = previewText,
            reserveTopReadingRow = content.reserveTopReadingRow
        ).also {
            snapshotKey = key
            snapshotValue = it
        }
    }

    fun moveCandidate(delta: Int): Boolean {
        if (!isActive()) return false
        val moved = if (session.hasDigits) {
            session.moveCandidate(delta, snapshot().paged?.candidates?.size ?: 0)
        } else {
            predictionSession.moveCandidate(delta)
        }
        if (!moved) return false
        advanceRevision(contentChanged = false)
        refreshUi()
        return true
    }

    fun moveSelectionTo(index: Int): Boolean {
        if (!isActive()) return false
        val moved = if (session.hasDigits) {
            session.setCandidateIndex(index, snapshot().paged?.candidates?.size ?: 0)
        } else {
            predictionSession.setCandidateIndex(index)
        }
        if (!moved) return false
        advanceRevision(contentChanged = false)
        return true
    }

    fun commitCandidate(
        index: Int? = null,
        appendSpace: Boolean = true,
        continuePrediction: Boolean = appendSpace
    ): Boolean {
        if (!isActive()) return false
        val selected = if (session.hasDigits) {
            session.selectedRawCandidate(pairRankedCandidates(), index) ?: run {
                reset()
                return true
            }
        } else {
            predictionSession.selectedCandidate(index) ?: return false
        }
        commitText(applyCaseToWord(selected) + if (appendSpace) " " else "")
        session.reset()
        if (continuePrediction) {
            rememberCommittedWord(selected)
        } else {
            // Punctuation/Return confirmation must not expose an unrelated next-word prediction.
            predictionSession.reset()
            predictionCandidatesGeneration = Long.MIN_VALUE
        }
        consumeShiftOnceInternal()
        advanceRevision()
        refreshUi()
        return true
    }

    fun backspace(): Boolean {
        if (!isActive()) return false
        val changed = if (session.hasDigits) {
            session.backspace()
        } else {
            predictionSession.hide()
        }
        if (!changed) return false
        advanceRevision()
        refreshUi()
        return true
    }

    fun applyCase(char: Char): Char = when (caseState) {
        CaseState.OFF -> char
        CaseState.SHIFT_ONCE, CaseState.CAPS -> char.uppercaseChar()
    }

    fun consumeShiftOnce() {
        if (consumeShiftOnceInternal()) advanceRevision()
    }

    fun cycleCase(): String {
        caseState = when (caseState) {
            CaseState.OFF -> CaseState.SHIFT_ONCE
            CaseState.SHIFT_ONCE -> CaseState.CAPS
            CaseState.CAPS -> CaseState.OFF
        }
        advanceRevision()
        refreshUi()
        return caseLabel
    }

    fun recordLearningChar(char: Char) {
        if (!shouldLearnWords()) {
            learningWord.clear()
            return
        }
        if (char in 'a'..'z' || char in 'A'..'Z') {
            learningWord.append(char.lowercaseChar())
        } else {
            flushLearningWord()
        }
    }

    fun flushLearningWord() {
        if (!shouldLearnWords()) {
            learningWord.clear()
            return
        }
        if (learningWord.isNotEmpty()) {
            val word = learningWord.toString()
            learnWord(word)
            rememberCommittedWord(word)
            learningWord.clear()
        }
    }

    private fun candidateContent(key: CandidateContentKey): CandidateContent {
        if (key == candidateContentKey) return requireNotNull(candidateContentValue)
        val content = when {
            !key.active -> CandidateContent(CandidateSource.NONE, emptyArray())
            session.hasDigits -> {
                val rawCandidates = pairRankedCandidates(
                    dictionaryIsReady = key.dictionaryReady,
                    dictionaryGeneration = key.dictionaryGeneration,
                    predictionIsReady = key.predictionReady,
                    predictionGeneration = key.predictionGeneration
                )
                val shown = session.visibleCandidates(
                    candidates = rawCandidates,
                    showNoMatch = key.dictionaryReady,
                    transform = ::applyCaseToWord
                ).map { FcitxEvent.Candidate(label = "", text = it, comment = "") }
                if (!key.dictionaryReady && shown.isEmpty()) {
                    CandidateContent(CandidateSource.NONE, emptyArray())
                } else {
                    CandidateContent(
                        source = CandidateSource.TYPED,
                        candidates = shown.toTypedArray(),
                        rawTypedCandidates = rawCandidates
                    )
                }
            }
            !key.predictionReady -> CandidateContent(CandidateSource.NONE, emptyArray())
            else -> {
                val shown = predictionSession.rawCandidates().map {
                    FcitxEvent.Candidate(label = "", text = applyCaseToWord(it), comment = "")
                }
                if (shown.isEmpty()) {
                    CandidateContent(CandidateSource.NONE, emptyArray())
                } else {
                    CandidateContent(
                        source = CandidateSource.PREDICTION,
                        candidates = shown.toTypedArray(),
                        reserveTopReadingRow = true
                    )
                }
            }
        }
        candidateContentKey = key
        candidateContentValue = content
        return content
    }

    private fun paged(
        candidates: Array<FcitxEvent.Candidate>,
        cursor: Int
    ) = FcitxEvent.PagedCandidateEvent.Data(
        candidates = candidates,
        cursorIndex = cursor,
        layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
        hasPrev = false,
        hasNext = false
    )

    private fun applyCaseToWord(word: String): String = when (caseState) {
        CaseState.OFF -> word
        CaseState.SHIFT_ONCE -> word.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        CaseState.CAPS -> word.uppercase()
    }

    private fun rawCandidates(
        dictionaryIsReady: Boolean = dictionaryReady(),
        dictionaryGeneration: Long = dictionaryGeneration()
    ): List<String> {
        val key = RawLookupKey(
            digits = session.digitSequence,
            dictionaryGeneration = dictionaryGeneration,
            dictionaryReady = dictionaryIsReady
        )
        if (key == rawLookupKey) return rawLookupValue
        return candidateProvider(key.digits, candidateLimit).also {
            rawLookupKey = key
            rawLookupValue = it
        }
    }

    private fun pairRankedCandidates(
        dictionaryIsReady: Boolean = dictionaryReady(),
        dictionaryGeneration: Long = dictionaryGeneration(),
        predictionIsReady: Boolean = predictionReady(),
        predictionGeneration: Long = predictionGeneration()
    ): List<String> {
        val rawCandidates = rawCandidates(dictionaryIsReady, dictionaryGeneration)
        val currentRawLookupKey = requireNotNull(rawLookupKey)
        val key = PairRankCacheKey(
            rawLookupKey = currentRawLookupKey,
            previousWords = recentWords.toList(),
            predictionGeneration = predictionGeneration,
            predictionReady = predictionIsReady
        )
        if (key == pairRankCacheKey) return pairRankCacheValue
        return rankCandidatesByPreviousWord(
            candidates = rawCandidates,
            digits = currentRawLookupKey.digits,
            previousWords = key.previousWords,
            predictionIsReady = predictionIsReady
        ).also {
            pairRankCacheKey = key
            pairRankCacheValue = it
        }
    }

    private fun rankCandidatesByPreviousWord(
        candidates: List<String>,
        digits: String,
        previousWords: List<String>,
        predictionIsReady: Boolean
    ): List<String> {
        if (candidates.size < 2 || previousWords.isEmpty() || digits.isEmpty() || !predictionIsReady) {
            return candidates
        }
        val predictionRank = predictionProvider(previousWords, candidateLimit)
            .filter { T9EnglishDictionary.t9DigitsForWord(it) == digits }
            .mapIndexed { index, word -> word.lowercase(Locale.US) to index }
            .toMap()
        if (predictionRank.isEmpty()) return candidates
        val indexed = candidates.withIndex()
        val ranked = indexed.filter { (_, word) -> predictionRank.containsKey(word.lowercase(Locale.US)) }
        if (ranked.isEmpty()) return candidates
        return buildList(candidates.size) {
            ranked.sortedWith(
                compareBy<IndexedValue<String>> {
                    predictionRank[it.value.lowercase(Locale.US)] ?: Int.MAX_VALUE
                }.thenBy { it.index }
            ).forEach { add(it.value) }
            indexed.filterNot { (_, word) -> predictionRank.containsKey(word.lowercase(Locale.US)) }
                .forEach { add(it.value) }
        }
    }

    private fun rememberCommittedWord(rawWord: String) {
        val word = SmartEnglishPredictionDictionary.normalizePredictionWord(rawWord) ?: return
        val previous = recentWords.lastOrNull()
        if (shouldLearnWords() && previous != null) {
            learnPredictionPair(previous, word)
        }
        recentWords += word
        while (recentWords.size > RecentWordContextSize) {
            recentWords.removeFirst()
        }
        predictionSession.updateContext(recentWords.toList())
        predictionCandidatesGeneration = predictionGeneration()
    }

    private fun consumeShiftOnceInternal(): Boolean {
        if (caseState != CaseState.SHIFT_ONCE) return false
        caseState = CaseState.OFF
        return true
    }

    private fun advanceRevision(contentChanged: Boolean = true) {
        inputRevision += 1
        if (contentChanged) contentRevision += 1
        snapshotKey = null
        snapshotValue = null
    }

    companion object {
        private const val RecentWordContextSize = 3
    }
}

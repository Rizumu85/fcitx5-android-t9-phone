/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import java.util.Locale

internal class SmartEnglishLifecycle(
    candidateProvider: (digits: String, limit: Int) -> List<String>,
    private val predictionProvider: (previousWords: List<String>, limit: Int) -> List<String>,
    private val learnWord: (String) -> Unit,
    private val learnPredictionPair: (String, String) -> Unit,
    private val dictionaryReady: () -> Boolean,
    private val predictionReady: () -> Boolean,
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

    private val session = SmartEnglishT9Session(
        candidateProvider = candidateProvider,
        candidateLimit = candidateLimit,
        noMatchText = noMatchText
    )
    private val predictionSession = SmartEnglishPredictionSession(
        predictionProvider = predictionProvider,
        candidateLimit = candidateLimit
    )

    // Candidate UI asks for paged data and presentation separately. Keep TT9-style
    // pair reranking stable within one input snapshot so those paths do not repeat
    // prediction lookups or drift in ordering during a visual update.
    private var pairRankCacheKey: PairRankCacheKey? = null
    private var pairRankCacheValue: List<String> = emptyList()
    private val learningWord = StringBuilder()
    private val recentWords = ArrayDeque<String>()
    private var caseState = CaseState.OFF

    val hasDigits: Boolean
        get() = session.hasDigits

    val hasCandidates: Boolean
        get() = session.hasDigits || predictionSession.isVisible

    val caseLabel: String
        get() = when (caseState) {
            CaseState.OFF -> "abc"
            CaseState.SHIFT_ONCE -> "Abc"
            CaseState.CAPS -> "ABC"
        }

    fun appendDigit(digit: Int) {
        predictionSession.reset()
        session.appendDigit(digit)
        invalidatePairRanking()
        refreshUi()
    }

    fun reset() {
        session.reset()
        predictionSession.reset()
        recentWords.clear()
        invalidatePairRanking()
        refreshUi()
    }

    fun paged(): FcitxEvent.PagedCandidateEvent.Data? {
        if (!isActive()) return null
        if (!session.hasDigits) {
            return predictionPaged()
        }
        val rawCandidates = pairRankedCandidates()
        if (!dictionaryReady() && rawCandidates.isEmpty()) return null
        val shown = session.visibleCandidates(
            candidates = rawCandidates,
            showNoMatch = dictionaryReady(),
            transform = ::applyCaseToWord
        ).map {
            FcitxEvent.Candidate(label = "", text = it, comment = "")
        }
        if (shown.isEmpty()) return null
        val cursor = session.cursor.coerceIn(shown.indices)
        return FcitxEvent.PagedCandidateEvent.Data(
            candidates = shown.toTypedArray(),
            cursorIndex = cursor,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
    }

    fun presentation(formatText: (String) -> FormattedText?): T9PresentationState? {
        if (!isActive()) return null
        if (!session.hasDigits) {
            return if (predictionSession.isVisible && predictionReady()) {
                val preview = predictionSession.selectedCandidate()?.let(::applyCaseToWord)
                T9PresentationState(
                    topReading = preview?.let(formatText),
                    pinyinOptions = emptyList(),
                    reserveTopReadingRow = true
                )
            } else {
                null
            }
        }
        val rawCandidates = pairRankedCandidates()
        if (!dictionaryReady() && rawCandidates.isEmpty()) return null
        val preview = session.inputPreviewText(rawCandidates)
        return T9PresentationState(
            topReading = formatText(applyCaseToWord(preview)),
            pinyinOptions = emptyList()
        )
    }

    fun moveCandidate(delta: Int): Boolean {
        if (!isActive()) return false
        val moved = if (session.hasDigits) {
            session.moveCandidate(delta)
        } else {
            predictionSession.moveCandidate(delta)
        }
        if (!moved) return false
        refreshUi()
        return true
    }

    fun setCandidateIndex(index: Int): Boolean {
        if (!isActive()) return false
        val moved = if (session.hasDigits) {
            session.setCandidateIndex(index)
        } else {
            predictionSession.setCandidateIndex(index)
        }
        if (!moved) return false
        refreshUi()
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
        invalidatePairRanking()
        if (continuePrediction) {
            rememberCommittedWord(selected)
        } else {
            // 1/# confirmation is meant to settle the visible word before a
            // punctuation or return action; keeping next-word predictions open
            // would make the follow-up key confirm an unrelated suggestion.
            predictionSession.reset()
        }
        consumeShiftOnce()
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
        invalidatePairRanking()
        refreshUi()
        return true
    }

    fun applyCase(char: Char): Char = when (caseState) {
        CaseState.OFF -> char
        CaseState.SHIFT_ONCE, CaseState.CAPS -> char.uppercaseChar()
    }

    fun consumeShiftOnce() {
        if (caseState == CaseState.SHIFT_ONCE) {
            caseState = CaseState.OFF
        }
    }

    fun cycleCase(): String {
        caseState = when (caseState) {
            CaseState.OFF -> CaseState.SHIFT_ONCE
            CaseState.SHIFT_ONCE -> CaseState.CAPS
            CaseState.CAPS -> CaseState.OFF
        }
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

    private fun predictionPaged(): FcitxEvent.PagedCandidateEvent.Data? {
        if (!predictionReady()) return null
        val shown = predictionSession.rawCandidates().map {
            FcitxEvent.Candidate(label = "", text = applyCaseToWord(it), comment = "")
        }
        if (shown.isEmpty()) return null
        val cursor = predictionSession.cursor.coerceIn(shown.indices)
        return FcitxEvent.PagedCandidateEvent.Data(
            candidates = shown.toTypedArray(),
            cursorIndex = cursor,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
    }

    private fun applyCaseToWord(word: String): String = when (caseState) {
        CaseState.OFF -> word
        CaseState.SHIFT_ONCE -> word.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        CaseState.CAPS -> word.uppercase()
    }

    private fun pairRankedCandidates(): List<String> {
        val rawCandidates = session.rawCandidates()
        val key = PairRankCacheKey(
            digits = session.digitSequence,
            previousWords = recentWords.toList(),
            candidates = rawCandidates
        )
        if (key == pairRankCacheKey) return pairRankCacheValue
        return rankCandidatesByPreviousWord(rawCandidates, key.digits).also {
            pairRankCacheKey = key
            pairRankCacheValue = it
        }
    }

    private fun invalidatePairRanking() {
        pairRankCacheKey = null
        pairRankCacheValue = emptyList()
    }

    private fun rankCandidatesByPreviousWord(
        candidates: List<String>,
        digits: String
    ): List<String> {
        if (candidates.size < 2 || recentWords.isEmpty() || digits.isEmpty() || !predictionReady()) {
            return candidates
        }
        val predictionRank = predictionProvider(recentWords.toList(), candidateLimit)
            .filter { T9EnglishDictionary.t9DigitsForWord(it) == digits }
            .mapIndexed { index, word -> word.lowercase(Locale.US) to index }
            .toMap()
        if (predictionRank.isEmpty()) return candidates
        val indexed = candidates.withIndex()
        val ranked = indexed.filter { (_, word) -> predictionRank.containsKey(word.lowercase(Locale.US)) }
        if (ranked.isEmpty()) return candidates
        return buildList(candidates.size) {
            ranked.sortedWith(
                compareBy<IndexedValue<String>> { predictionRank[it.value.lowercase(Locale.US)] ?: Int.MAX_VALUE }
                    .thenBy { it.index }
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
    }

    companion object {
        private const val RecentWordContextSize = 3
    }

    private data class PairRankCacheKey(
        val digits: String,
        val previousWords: List<String>,
        val candidates: List<String>
    )
}

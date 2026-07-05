/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText

class SmartEnglishT9Controller private constructor(
    private val dictionary: T9EnglishDictionary?,
    private val predictionDictionary: SmartEnglishPredictionDictionary?,
    candidateProvider: (digits: String, limit: Int) -> List<String>,
    predictionProvider: (previousWords: List<String>, limit: Int) -> List<String>,
    private val learnWord: (String) -> Unit,
    private val learnPredictionPair: (String, String) -> Unit,
    private val dictionaryReady: () -> Boolean,
    private val predictionReady: () -> Boolean,
    candidateLimit: Int,
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
    private val learningWord = StringBuilder()
    private val recentWords = ArrayDeque<String>()
    private var caseState = CaseState.OFF

    constructor(
        dictionary: T9EnglishDictionary = T9EnglishDictionary(),
        predictionDictionary: SmartEnglishPredictionDictionary = SmartEnglishPredictionDictionary(),
        candidateLimit: Int,
        noMatchText: String,
        isActive: () -> Boolean,
        shouldLearnWords: () -> Boolean,
        commitText: (String) -> Unit,
        refreshUi: () -> Unit
    ) : this(
        dictionary = dictionary,
        predictionDictionary = predictionDictionary,
        candidateProvider = dictionary::candidatesFor,
        predictionProvider = predictionDictionary::predictionsAfter,
        learnWord = dictionary::learn,
        learnPredictionPair = predictionDictionary::learn,
        dictionaryReady = dictionary::isReady,
        predictionReady = predictionDictionary::isReady,
        candidateLimit = candidateLimit,
        noMatchText = noMatchText,
        isActive = isActive,
        shouldLearnWords = shouldLearnWords,
        commitText = commitText,
        refreshUi = refreshUi
    )

    constructor(
        candidateProvider: (digits: String, limit: Int) -> List<String>,
        predictionProvider: (previousWords: List<String>, limit: Int) -> List<String> = { _, _ -> emptyList() },
        learnWord: (String) -> Unit = {},
        learnPredictionPair: (String, String) -> Unit = { _, _ -> },
        dictionaryReady: () -> Boolean = { true },
        predictionReady: () -> Boolean = { true },
        candidateLimit: Int,
        noMatchText: String,
        isActive: () -> Boolean,
        shouldLearnWords: () -> Boolean,
        commitText: (String) -> Unit,
        refreshUi: () -> Unit
    ) : this(
        dictionary = null,
        predictionDictionary = null,
        candidateProvider = candidateProvider,
        predictionProvider = predictionProvider,
        learnWord = learnWord,
        learnPredictionPair = learnPredictionPair,
        dictionaryReady = dictionaryReady,
        predictionReady = predictionReady,
        candidateLimit = candidateLimit,
        noMatchText = noMatchText,
        isActive = isActive,
        shouldLearnWords = shouldLearnWords,
        commitText = commitText,
        refreshUi = refreshUi
    )

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

    val isDictionaryReady: Boolean
        get() = dictionaryReady()

    fun preloadDictionary() {
        dictionary?.preload()
        predictionDictionary?.preload()
    }

    fun appendDigit(digit: Int) {
        predictionSession.reset()
        session.appendDigit(digit)
        refreshUi()
    }

    fun reset() {
        session.reset()
        predictionSession.reset()
        recentWords.clear()
        refreshUi()
    }

    fun paged(): FcitxEvent.PagedCandidateEvent.Data? {
        if (!isActive()) return null
        if (!session.hasDigits) {
            return predictionPaged()
        }
        val rawCandidates = session.rawCandidates()
        if (!isDictionaryReady && rawCandidates.isEmpty()) return null
        val shown = session.visibleCandidates(
            candidates = rawCandidates,
            showNoMatch = isDictionaryReady,
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
        val rawCandidates = session.rawCandidates()
        if (!isDictionaryReady && rawCandidates.isEmpty()) return null
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

    fun commitCandidate(index: Int? = null): Boolean {
        if (!isActive()) return false
        val selected = if (session.hasDigits) {
            session.selectedRawCandidate(index) ?: run {
                reset()
                return true
            }
        } else {
            predictionSession.selectedCandidate(index) ?: return false
        }
        commitText("${applyCaseToWord(selected)} ")
        session.reset()
        rememberCommittedWord(selected)
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
        refreshUi()
        return true
    }

    fun applyCase(char: Char): Char = when (caseState) {
        CaseState.OFF -> char
        CaseState.SHIFT_ONCE, CaseState.CAPS -> char.uppercaseChar()
    }

    fun applyCaseToWord(word: String): String = when (caseState) {
        CaseState.OFF -> word
        CaseState.SHIFT_ONCE -> word.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        CaseState.CAPS -> word.uppercase()
    }

    fun consumeShiftOnce() {
        if (caseState == CaseState.SHIFT_ONCE) {
            caseState = CaseState.OFF
        }
    }

    fun toggleShiftOnce(): String {
        caseState = when (caseState) {
            CaseState.OFF -> CaseState.SHIFT_ONCE
            CaseState.SHIFT_ONCE, CaseState.CAPS -> CaseState.OFF
        }
        return caseLabel
    }

    fun toggleCaps(): String {
        caseState = when (caseState) {
            CaseState.OFF, CaseState.SHIFT_ONCE -> CaseState.CAPS
            CaseState.CAPS -> CaseState.OFF
        }
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
}

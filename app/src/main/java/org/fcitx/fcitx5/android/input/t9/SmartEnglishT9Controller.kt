/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText

class SmartEnglishT9Controller private constructor(
    private val dictionary: T9EnglishDictionary?,
    candidateProvider: (digits: String, limit: Int) -> List<String>,
    private val learnWord: (String) -> Unit,
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
    private val learningWord = StringBuilder()
    private var caseState = CaseState.OFF

    constructor(
        dictionary: T9EnglishDictionary = T9EnglishDictionary(),
        candidateLimit: Int,
        noMatchText: String,
        isActive: () -> Boolean,
        shouldLearnWords: () -> Boolean,
        commitText: (String) -> Unit,
        refreshUi: () -> Unit
    ) : this(
        dictionary = dictionary,
        candidateProvider = dictionary::candidatesFor,
        learnWord = dictionary::learn,
        candidateLimit = candidateLimit,
        noMatchText = noMatchText,
        isActive = isActive,
        shouldLearnWords = shouldLearnWords,
        commitText = commitText,
        refreshUi = refreshUi
    )

    constructor(
        candidateProvider: (digits: String, limit: Int) -> List<String>,
        learnWord: (String) -> Unit = {},
        candidateLimit: Int,
        noMatchText: String,
        isActive: () -> Boolean,
        shouldLearnWords: () -> Boolean,
        commitText: (String) -> Unit,
        refreshUi: () -> Unit
    ) : this(
        dictionary = null,
        candidateProvider = candidateProvider,
        learnWord = learnWord,
        candidateLimit = candidateLimit,
        noMatchText = noMatchText,
        isActive = isActive,
        shouldLearnWords = shouldLearnWords,
        commitText = commitText,
        refreshUi = refreshUi
    )

    val hasDigits: Boolean
        get() = session.hasDigits

    val caseLabel: String
        get() = when (caseState) {
            CaseState.OFF -> "abc"
            CaseState.SHIFT_ONCE -> "Abc"
            CaseState.CAPS -> "ABC"
        }

    fun preloadDictionary() {
        dictionary?.preload()
    }

    fun appendDigit(digit: Int) {
        session.appendDigit(digit)
        refreshUi()
    }

    fun reset() {
        session.reset()
        refreshUi()
    }

    fun paged(): FcitxEvent.PagedCandidateEvent.Data? {
        if (!isActive() || !session.hasDigits) return null
        val shown = session.visibleCandidates(::applyCaseToWord).map {
            FcitxEvent.Candidate(label = "", text = it, comment = "")
        }
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
        if (!isActive() || !session.hasDigits) return null
        val preview = session.inputPreviewText()
        return T9PresentationState(
            topReading = formatText(applyCaseToWord(preview)),
            pinyinOptions = emptyList()
        )
    }

    fun moveCandidate(delta: Int): Boolean {
        if (!isActive()) return false
        if (!session.moveCandidate(delta)) return false
        refreshUi()
        return true
    }

    fun setCandidateIndex(index: Int): Boolean {
        if (!isActive()) return false
        if (!session.setCandidateIndex(index)) return false
        refreshUi()
        return true
    }

    fun commitCandidate(index: Int? = null): Boolean {
        if (!isActive() || !session.hasDigits) return false
        val selected = session.selectedRawCandidate(index) ?: run {
            reset()
            return true
        }
        commitText(applyCaseToWord(selected))
        reset()
        consumeShiftOnce()
        return true
    }

    fun backspace(): Boolean {
        if (!isActive() || !session.backspace()) return false
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
            learnWord(learningWord.toString())
            learningWord.clear()
        }
    }
}

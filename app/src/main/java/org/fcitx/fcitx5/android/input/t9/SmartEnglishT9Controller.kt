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
    private val dictionaryReady: () -> Boolean,
    private val lifecycle: SmartEnglishLifecycle
) {
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
        dictionaryReady = dictionary::isReady,
        lifecycle = SmartEnglishLifecycle(
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
        dictionaryReady = dictionaryReady,
        lifecycle = SmartEnglishLifecycle(
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
    )

    val hasDigits: Boolean
        get() = lifecycle.hasDigits

    val hasCandidates: Boolean
        get() = lifecycle.hasCandidates

    val caseLabel: String
        get() = lifecycle.caseLabel

    val isDictionaryReady: Boolean
        get() = dictionaryReady()

    fun preloadDictionary() {
        dictionary?.preload()
        predictionDictionary?.preload()
    }

    fun appendDigit(digit: Int) =
        lifecycle.appendDigit(digit)

    fun reset() =
        lifecycle.reset()

    fun paged(): FcitxEvent.PagedCandidateEvent.Data? =
        lifecycle.paged()

    fun presentation(formatText: (String) -> FormattedText?): T9PresentationState? =
        lifecycle.presentation(formatText)

    fun moveCandidate(delta: Int): Boolean =
        lifecycle.moveCandidate(delta)

    fun setCandidateIndex(index: Int): Boolean =
        lifecycle.setCandidateIndex(index)

    fun commitCandidate(
        index: Int? = null,
        appendSpace: Boolean = true,
        continuePrediction: Boolean = appendSpace
    ): Boolean =
        lifecycle.commitCandidate(index, appendSpace, continuePrediction)

    fun backspace(): Boolean =
        lifecycle.backspace()

    fun applyCase(char: Char): Char =
        lifecycle.applyCase(char)

    fun consumeShiftOnce() =
        lifecycle.consumeShiftOnce()

    fun cycleCase(): String =
        lifecycle.cycleCase()

    fun recordLearningChar(char: Char) =
        lifecycle.recordLearningChar(char)

    fun flushLearningWord() =
        lifecycle.flushLearningWord()
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class SmartEnglishT9Controller private constructor(
    private val preloadResources: (() -> Unit)?,
    private val resourcesReady: () -> Boolean,
    private val lifecycle: SmartEnglishLifecycle
) {
    constructor(
        suggestionEngine: EnglishSuggestionEngine = EnglishSuggestionEngine.Shared,
        candidateLimit: Int,
        noMatchText: String,
        isActive: () -> Boolean,
        shouldLearnWords: () -> Boolean,
        commitText: (String) -> Unit,
        refreshUi: () -> Unit
    ) : this(
        preloadResources = suggestionEngine::preload,
        resourcesReady = suggestionEngine::isReady,
        lifecycle = SmartEnglishLifecycle(
            candidateProvider = suggestionEngine::candidatesForDigits,
            predictionProvider = suggestionEngine::predictionsAfter,
            learnWord = suggestionEngine::learnWord,
            learnPredictionPair = suggestionEngine::learnPair,
            dictionaryReady = suggestionEngine::isReady,
            predictionReady = suggestionEngine::isReady,
            dictionaryGeneration = suggestionEngine::dictionaryGeneration,
            predictionGeneration = suggestionEngine::predictionGeneration,
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
        dictionaryGeneration: () -> Long = { 0L },
        predictionGeneration: () -> Long = { 0L },
        candidateLimit: Int,
        noMatchText: String,
        isActive: () -> Boolean,
        shouldLearnWords: () -> Boolean,
        commitText: (String) -> Unit,
        refreshUi: () -> Unit
    ) : this(
        preloadResources = null,
        resourcesReady = { dictionaryReady() && predictionReady() },
        lifecycle = SmartEnglishLifecycle(
            candidateProvider = candidateProvider,
            predictionProvider = predictionProvider,
            learnWord = learnWord,
            learnPredictionPair = learnPredictionPair,
            dictionaryReady = dictionaryReady,
            predictionReady = predictionReady,
            dictionaryGeneration = dictionaryGeneration,
            predictionGeneration = predictionGeneration,
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

    val isReady: Boolean
        get() = resourcesReady()

    val shouldRefreshAfterWarmup: Boolean
        get() = lifecycle.shouldRefreshAfterWarmup

    fun preload() {
        preloadResources?.invoke()
    }

    fun appendDigit(digit: Int) =
        lifecycle.appendDigit(digit)

    fun reset() =
        lifecycle.reset()

    fun snapshot(): SmartEnglishSnapshot =
        lifecycle.snapshot()

    fun moveCandidate(delta: Int): Boolean =
        lifecycle.moveCandidate(delta)

    fun moveSelectionTo(index: Int): Boolean =
        lifecycle.moveSelectionTo(index)

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

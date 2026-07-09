/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class PhysicalT9KeyHostAdapter(
    private val state: State,
    private val punctuation: PunctuationActions,
    private val english: EnglishActions,
    private val candidates: CandidateActions,
    private val platform: PlatformActions
) : PhysicalT9KeyHandler.Host {

    data class State(
        val isInInputMode: () -> Boolean,
        val mode: () -> T9InputMode,
        val isSmartEnglishActive: () -> Boolean,
        val chineseComposing: () -> Boolean,
        val compositionKeyCount: () -> Int,
        val hasPendingPunctuation: () -> Boolean,
        val hasSmartEnglishDigits: () -> Boolean,
        val hasSmartEnglishCandidates: () -> Boolean,
        val hasMultiTapPendingChar: () -> Boolean,
        val hasTopPinyinCandidates: () -> Boolean,
        val hasBottomCandidateRow: () -> Boolean,
        val candidateFocus: () -> T9CandidateFocus,
        val keyHeldPastLongPressDelay: (PhysicalT9KeyHandler.KeyInput) -> Boolean
    )

    data class PunctuationActions(
        val commitShortcut: (Int) -> Boolean,
        val commit: () -> Boolean,
        val cancel: () -> Boolean,
        val showChineseCandidates: () -> Unit,
        val showEnglishCandidates: () -> Unit,
        val toggleSet: () -> Boolean,
    )

    data class EnglishActions(
        val commitSmartEnglishShortcut: (Int) -> Boolean,
        val appendSmartEnglishDigit: (Int) -> Unit,
        val resetSmartEnglish: () -> Unit,
        val commitSmartEnglishCandidate: (appendSpace: Boolean, continuePrediction: Boolean) -> Boolean,
        val moveSmartEnglishCandidate: (Int) -> Boolean,
        val smartEnglishBackspace: () -> Boolean,
        val flushLearningWord: () -> Unit,
        val cycleCase: () -> Unit,
        val handleMultiTapKey: (Int) -> Boolean,
        val commitMultiTapChar: () -> Boolean,
        val cancelMultiTapChar: () -> Unit
    )

    data class CandidateActions(
        val commitHanziShortcut: (Int) -> Boolean,
        val moveFocus: (T9CandidateFocus) -> Unit,
        val moveHighlightedPinyin: (Int) -> Boolean,
        val moveHighlightedBottomCandidate: (Int) -> Boolean,
        val offsetBottomCandidatePage: (Int) -> Boolean,
        val commitHighlightedPinyin: () -> Boolean,
        val commitHighlightedBottomCandidate: () -> Boolean,
        val commitChineseCandidateAndShowPunctuation: () -> Unit
    )

    data class PlatformActions(
        val switchToNextMode: () -> Unit,
        val commitText: (String) -> Unit,
        val commitNumberOperatorForKey: (keyCode: Int, fallbackDigit: Int) -> Boolean,
        val showNumberOperatorHintPanel: () -> Unit,
        val commitLiteralStar: () -> Unit,
        val handleReturnKey: () -> Unit,
        val forwardChineseT9KeyShortPress: (Int, PhysicalT9KeyHandler.KeyInput) -> Boolean,
        val forwardChineseT9SeparatorShortPress: () -> Boolean,
        val discardChineseCompositionForModeSwitch: () -> Unit
    )

    override val isInInputMode: Boolean
        get() = state.isInInputMode()

    override val mode: PhysicalT9KeyHandler.Mode
        get() = when (state.mode()) {
            T9InputMode.CHINESE -> PhysicalT9KeyHandler.Mode.CHINESE
            T9InputMode.ENGLISH -> PhysicalT9KeyHandler.Mode.ENGLISH
            T9InputMode.NUMBER -> PhysicalT9KeyHandler.Mode.NUMBER
        }

    override val isSmartEnglishActive: Boolean
        get() = state.isSmartEnglishActive()

    override val chineseComposing: Boolean
        get() = state.chineseComposing()

    override val compositionKeyCount: Int
        get() = state.compositionKeyCount()

    override val hasPendingPunctuation: Boolean
        get() = state.hasPendingPunctuation()

    override val hasSmartEnglishDigits: Boolean
        get() = state.hasSmartEnglishDigits()

    override val hasSmartEnglishCandidates: Boolean
        get() = state.hasSmartEnglishCandidates()

    override val hasMultiTapPendingChar: Boolean
        get() = state.hasMultiTapPendingChar()

    override val hasTopPinyinCandidates: Boolean
        get() = state.hasTopPinyinCandidates()

    override val hasBottomCandidateRow: Boolean
        get() = state.hasBottomCandidateRow()

    override val candidateFocus: PhysicalT9KeyHandler.CandidateFocus
        get() = when (state.candidateFocus()) {
            T9CandidateFocus.TOP -> PhysicalT9KeyHandler.CandidateFocus.TOP
            T9CandidateFocus.BOTTOM -> PhysicalT9KeyHandler.CandidateFocus.BOTTOM
        }

    override fun keyHeldPastLongPressDelay(input: PhysicalT9KeyHandler.KeyInput): Boolean =
        state.keyHeldPastLongPressDelay(input)

    override fun commitPendingPunctuationShortcut(keyCode: Int): Boolean =
        punctuation.commitShortcut(keyCode)

    override fun commitHanziShortcut(keyCode: Int): Boolean =
        candidates.commitHanziShortcut(keyCode)

    override fun commitSmartEnglishShortcut(keyCode: Int): Boolean =
        english.commitSmartEnglishShortcut(keyCode)

    override fun commitPendingPunctuation(): Boolean =
        punctuation.commit()

    override fun cancelPendingPunctuation(): Boolean =
        punctuation.cancel()

    override fun showChinesePunctuationCandidates() =
        punctuation.showChineseCandidates()

    override fun showEnglishPunctuationCandidates() =
        punctuation.showEnglishCandidates()

    override fun commitChineseCandidateAndShowPunctuation() =
        candidates.commitChineseCandidateAndShowPunctuation()

    override fun togglePendingPunctuationSet(): Boolean =
        punctuation.toggleSet()

    override fun switchToNextMode() =
        platform.switchToNextMode()

    override fun commitText(text: String) =
        platform.commitText(text)

    override fun commitNumberOperatorForKey(keyCode: Int, fallbackDigit: Int): Boolean =
        platform.commitNumberOperatorForKey(keyCode, fallbackDigit)

    override fun showNumberOperatorHintPanel() =
        platform.showNumberOperatorHintPanel()

    override fun commitLiteralStar() =
        platform.commitLiteralStar()

    override fun cycleEnglishCase() =
        english.cycleCase()

    override fun handleMultiTapKey(keyCode: Int): Boolean =
        english.handleMultiTapKey(keyCode)

    override fun commitMultiTapChar(): Boolean =
        english.commitMultiTapChar()

    override fun cancelMultiTapChar() =
        english.cancelMultiTapChar()

    override fun appendSmartEnglishDigit(digit: Int) =
        english.appendSmartEnglishDigit(digit)

    override fun resetSmartEnglishT9() =
        english.resetSmartEnglish()

    override fun commitSmartEnglishCandidate(
        appendSpace: Boolean,
        continuePrediction: Boolean
    ): Boolean =
        english.commitSmartEnglishCandidate(appendSpace, continuePrediction)

    override fun moveSmartEnglishCandidate(delta: Int): Boolean =
        english.moveSmartEnglishCandidate(delta)

    override fun smartEnglishBackspace(): Boolean =
        english.smartEnglishBackspace()

    override fun flushEnglishLearningWord() =
        english.flushLearningWord()

    override fun handleReturnKey() =
        platform.handleReturnKey()

    override fun forwardChineseT9KeyShortPress(
        keyCode: Int,
        input: PhysicalT9KeyHandler.KeyInput
    ): Boolean =
        platform.forwardChineseT9KeyShortPress(keyCode, input)

    override fun forwardChineseT9SeparatorShortPress(): Boolean =
        platform.forwardChineseT9SeparatorShortPress()

    override fun discardChineseCompositionForModeSwitch() =
        platform.discardChineseCompositionForModeSwitch()

    override fun moveCandidateFocus(focus: PhysicalT9KeyHandler.CandidateFocus) =
        candidates.moveFocus(
            when (focus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP -> T9CandidateFocus.TOP
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM -> T9CandidateFocus.BOTTOM
            }
        )

    override fun moveHighlightedPinyin(delta: Int): Boolean =
        candidates.moveHighlightedPinyin(delta)

    override fun moveHighlightedBottomCandidate(delta: Int): Boolean =
        candidates.moveHighlightedBottomCandidate(delta)

    override fun offsetBottomCandidatePage(delta: Int): Boolean =
        candidates.offsetBottomCandidatePage(delta)

    override fun commitHighlightedPinyin(): Boolean =
        candidates.commitHighlightedPinyin()

    override fun commitHighlightedBottomCandidate(): Boolean =
        candidates.commitHighlightedBottomCandidate()
}

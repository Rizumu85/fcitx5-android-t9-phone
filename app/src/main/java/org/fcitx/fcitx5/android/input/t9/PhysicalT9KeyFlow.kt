/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class PhysicalT9KeyFlow {

    data class State(
        val mode: PhysicalT9KeyHandler.Mode,
        val isSmartEnglishActive: Boolean,
        val chineseComposing: Boolean,
        val compositionKeyCount: Int,
        val hasPendingPunctuation: Boolean,
        val hasSmartEnglishDigits: Boolean,
        val hasSmartEnglishCandidates: Boolean,
        val hasMultiTapPendingChar: Boolean,
        val hasTopReadingCandidates: Boolean,
        val hasBottomCandidateRow: Boolean,
        val candidateFocus: PhysicalT9KeyHandler.CandidateFocus,
        val heldPastLongPressDelay: Boolean,
        val chineseScheme: ChineseT9Scheme = ChineseT9Scheme.PINYIN,
        val idleLongZeroVoiceEnabled: Boolean = false
    )

    data class Decision(
        val handled: Boolean,
        val commands: List<Command> = emptyList(),
        val consumedKeyUp: Int? = null
    )

    enum class BottomCandidateFallback {
        NONE,
        PENDING_PUNCTUATION,
        SMART_ENGLISH
    }

    sealed class Command {
        data class CommitPendingPunctuationShortcut(val keyCode: Int) : Command()
        data class CommitPendingPunctuationShortcutOrText(
            val keyCode: Int,
            val text: String
        ) : Command()
        object CommitPendingPunctuation : Command()
        object CancelPendingPunctuation : Command()
        object CancelMultiTapChar : Command()
        object ShowChinesePunctuationCandidates : Command()
        object ShowEnglishPunctuationCandidates : Command()
        object CommitChineseCandidateAndShowPunctuation : Command()
        data class HandleMultiTapKey(val keyCode: Int) : Command()
        object CommitMultiTapChar : Command()
        data class CommitSmartEnglishShortcut(val keyCode: Int) : Command()
        data class CommitSmartEnglishCandidate(
            val appendSpace: Boolean,
            val continuePrediction: Boolean
        ) : Command()
        object CommitSmartEnglishCandidateOrMultiTap : Command()
        object CommitEnglishPendingOrSpace : Command()
        object CommitEnglishPendingOrReturn : Command()
        data class AppendSmartEnglishDigit(val digit: Int) : Command()
        object ResetSmartEnglishT9 : Command()
        object FlushEnglishLearningWord : Command()
        data class MoveBottomCandidate(
            val delta: Int,
            val fallbackSmartEnglishDelta: Int? = null
        ) : Command()
        data class OffsetBottomCandidatePage(
            val delta: Int
        ) : Command()
        data class MoveCandidateFocus(
            val focus: PhysicalT9KeyHandler.CandidateFocus
        ) : Command()
        data class MoveHighlightedReading(val delta: Int) : Command()
        object CommitHighlightedReading : Command()
        data class CommitBottomCandidate(
            val fallback: BottomCandidateFallback
        ) : Command()
        data class SmartEnglishDelete(
            val hasPendingPunctuation: Boolean
        ) : Command()
        data class CommitText(val text: String) : Command()
        data class CommitHanziShortcut(val keyCode: Int) : Command()
        data class ForwardChineseT9KeyShortPress(val keyCode: Int) : Command()
        object ForwardChineseT9SeparatorShortPress : Command()
        object CommitChineseCodePreview : Command()
        object CycleChineseSchemeOrCommitLiteralStar : Command()
        object TogglePendingPunctuationSet : Command()
        object CycleEnglishCase : Command()
        object HandleReturnKey : Command()
        object SwitchToNextMode : Command()
        object SwitchToVoiceInput : Command()
        object DiscardChineseCompositionForModeSwitch : Command()
        data class CommitNumberOperatorForKey(
            val keyCode: Int,
            val fallbackDigit: Int
        ) : Command()
        object ShowNumberOperatorHintPanel : Command()
        object CommitLiteralStar : Command()
    }

    // Mode modules stay private to this flow so callers still test one command interface.
    private val session = PhysicalT9KeyFlowSession()
    private val chineseFlow = PhysicalT9ChineseKeyFlow(session)
    private val englishFlow = PhysicalT9EnglishKeyFlow(session)
    private val numberFlow = PhysicalT9NumberKeyFlow(session)

    fun resetSmartEnglishPendingDigit() {
        session.resetSmartEnglishPendingDigit()
    }

    fun handle(input: PhysicalT9KeyHandler.KeyInput, state: State): Decision? {
        return when (state.mode) {
            PhysicalT9KeyHandler.Mode.ENGLISH -> englishFlow.handle(input, state)
            PhysicalT9KeyHandler.Mode.NUMBER -> numberFlow.handle(input, state)
            PhysicalT9KeyHandler.Mode.CHINESE -> chineseFlow.handle(input, state)
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

internal data class PhysicalT9TracePlan(
    val path: String,
    val completionKind: T9ResponsivenessTrace.CompletionKind,
    val requiresSourceEvent: Boolean,
    val candidateFrameIsSynchronous: Boolean
)

internal object PhysicalT9TracePlanner {
    fun plan(
        state: PhysicalT9KeyFlow.State,
        decision: PhysicalT9KeyFlow.Decision,
        forwardsThroughOuterRoute: Boolean
    ): PhysicalT9TracePlan? {
        if (decision.commands.isEmpty() && !forwardsThroughOuterRoute) return null
        val requiresSourceEvent = forwardsThroughOuterRoute ||
            decision.commands.any { it.waitsForFcitxSourceEvent() }
        val completionKind = when {
            requiresSourceEvent -> T9ResponsivenessTrace.CompletionKind.CANDIDATE_FRAME
            decision.commands.any { it.updatesInputSurface() } ->
                T9ResponsivenessTrace.CompletionKind.INPUT_SURFACE_FRAME
            decision.commands.any { it.updatesCandidateSurface(state) } ->
                T9ResponsivenessTrace.CompletionKind.CANDIDATE_FRAME
            else -> T9ResponsivenessTrace.CompletionKind.EFFECT
        }
        return PhysicalT9TracePlan(
            path = "${state.modePath()}/${decision.actionName(forwardsThroughOuterRoute)}",
            completionKind = completionKind,
            requiresSourceEvent = requiresSourceEvent,
            candidateFrameIsSynchronous = completionKind ==
                T9ResponsivenessTrace.CompletionKind.CANDIDATE_FRAME &&
                decision.commands.any { it.rendersCandidateSynchronously() }
        )
    }

    private fun PhysicalT9KeyFlow.State.modePath(): String = when (mode) {
        PhysicalT9KeyHandler.Mode.CHINESE -> "CHINESE/${chineseScheme.name}"
        PhysicalT9KeyHandler.Mode.ENGLISH -> if (isSmartEnglishActive) "SMART_ENGLISH" else "ENGLISH"
        PhysicalT9KeyHandler.Mode.NUMBER -> "NUMBER"
    }

    private fun PhysicalT9KeyFlow.Decision.actionName(forwardsThroughOuterRoute: Boolean): String {
        if (forwardsThroughOuterRoute || commands.any { it.waitsForFcitxSourceEvent() }) {
            return "INPUT"
        }
        return when {
            commands.any { it.isPunctuationAction() } -> "PUNCTUATION"
            commands.any { it.isNavigationAction() } -> "NAVIGATION"
            commands.any { it.isCandidateCommitAction() } -> "CANDIDATE_COMMIT"
            commands.any { it is PhysicalT9KeyFlow.Command.SmartEnglishDelete } -> "DELETE"
            commands.any { it is PhysicalT9KeyFlow.Command.CycleEnglishCase } -> "CASE"
            commands.any {
                it is PhysicalT9KeyFlow.Command.SwitchToNextMode ||
                    it is PhysicalT9KeyFlow.Command.CycleChineseSchemeOrCommitLiteralStar
            } -> "MODE"
            commands.any { it is PhysicalT9KeyFlow.Command.SwitchToVoiceInput } -> "VOICE"
            commands.any { it is PhysicalT9KeyFlow.Command.HandleReturnKey } -> "RETURN"
            commands.any {
                it is PhysicalT9KeyFlow.Command.CommitNumberOperatorForKey ||
                    it is PhysicalT9KeyFlow.Command.ShowNumberOperatorHintPanel
            } -> "NUMBER_OPERATOR"
            else -> "TEXT_INPUT"
        }
    }

    private fun PhysicalT9KeyFlow.Command.waitsForFcitxSourceEvent(): Boolean = when (this) {
        is PhysicalT9KeyFlow.Command.ForwardChineseT9KeyShortPress,
        PhysicalT9KeyFlow.Command.ForwardChineseT9SeparatorShortPress -> true
        else -> false
    }

    private fun PhysicalT9KeyFlow.Command.updatesInputSurface(): Boolean = when (this) {
        PhysicalT9KeyFlow.Command.CycleEnglishCase,
        PhysicalT9KeyFlow.Command.SwitchToNextMode,
        PhysicalT9KeyFlow.Command.CycleChineseSchemeOrCommitLiteralStar,
        PhysicalT9KeyFlow.Command.ShowNumberOperatorHintPanel -> true
        else -> false
    }

    private fun PhysicalT9KeyFlow.Command.updatesCandidateSurface(
        state: PhysicalT9KeyFlow.State
    ): Boolean = when (this) {
        is PhysicalT9KeyFlow.Command.CommitPendingPunctuationShortcut,
        is PhysicalT9KeyFlow.Command.CommitPendingPunctuationShortcutOrText,
        PhysicalT9KeyFlow.Command.CommitPendingPunctuation,
        PhysicalT9KeyFlow.Command.CancelPendingPunctuation,
        PhysicalT9KeyFlow.Command.ShowChinesePunctuationCandidates,
        PhysicalT9KeyFlow.Command.ShowEnglishPunctuationCandidates,
        PhysicalT9KeyFlow.Command.CommitChineseCandidateAndShowPunctuation,
        is PhysicalT9KeyFlow.Command.CommitSmartEnglishShortcut,
        is PhysicalT9KeyFlow.Command.CommitSmartEnglishCandidate -> true
        PhysicalT9KeyFlow.Command.CommitSmartEnglishCandidateOrMultiTap ->
            state.isSmartEnglishActive && state.hasSmartEnglishCandidates
        PhysicalT9KeyFlow.Command.CommitEnglishPendingOrSpace,
        PhysicalT9KeyFlow.Command.CommitEnglishPendingOrReturn ->
            state.hasPendingPunctuation ||
                (state.isSmartEnglishActive &&
                    (state.hasSmartEnglishDigits || state.hasSmartEnglishCandidates))
        is PhysicalT9KeyFlow.Command.AppendSmartEnglishDigit -> true
        PhysicalT9KeyFlow.Command.ResetSmartEnglishT9 ->
            state.hasSmartEnglishDigits || state.hasSmartEnglishCandidates
        is PhysicalT9KeyFlow.Command.MoveBottomCandidate,
        is PhysicalT9KeyFlow.Command.OffsetBottomCandidatePage,
        is PhysicalT9KeyFlow.Command.MoveCandidateFocus,
        is PhysicalT9KeyFlow.Command.MoveHighlightedReading,
        PhysicalT9KeyFlow.Command.CommitHighlightedReading,
        is PhysicalT9KeyFlow.Command.CommitBottomCandidate,
        is PhysicalT9KeyFlow.Command.SmartEnglishDelete,
        is PhysicalT9KeyFlow.Command.CommitHanziShortcut,
        PhysicalT9KeyFlow.Command.TogglePendingPunctuationSet -> true
        PhysicalT9KeyFlow.Command.CommitChineseCodePreview -> state.chineseComposing
        PhysicalT9KeyFlow.Command.DiscardChineseCompositionForModeSwitch -> state.chineseComposing
        else -> false
    }

    private fun PhysicalT9KeyFlow.Command.isPunctuationAction(): Boolean = when (this) {
        is PhysicalT9KeyFlow.Command.CommitPendingPunctuationShortcut,
        is PhysicalT9KeyFlow.Command.CommitPendingPunctuationShortcutOrText,
        PhysicalT9KeyFlow.Command.CommitPendingPunctuation,
        PhysicalT9KeyFlow.Command.CancelPendingPunctuation,
        PhysicalT9KeyFlow.Command.ShowChinesePunctuationCandidates,
        PhysicalT9KeyFlow.Command.ShowEnglishPunctuationCandidates,
        PhysicalT9KeyFlow.Command.CommitChineseCandidateAndShowPunctuation,
        PhysicalT9KeyFlow.Command.TogglePendingPunctuationSet -> true
        else -> false
    }

    private fun PhysicalT9KeyFlow.Command.isNavigationAction(): Boolean = when (this) {
        is PhysicalT9KeyFlow.Command.MoveBottomCandidate,
        is PhysicalT9KeyFlow.Command.OffsetBottomCandidatePage,
        is PhysicalT9KeyFlow.Command.MoveCandidateFocus,
        is PhysicalT9KeyFlow.Command.MoveHighlightedReading -> true
        else -> false
    }

    private fun PhysicalT9KeyFlow.Command.isCandidateCommitAction(): Boolean = when (this) {
        is PhysicalT9KeyFlow.Command.CommitSmartEnglishShortcut,
        is PhysicalT9KeyFlow.Command.CommitSmartEnglishCandidate,
        PhysicalT9KeyFlow.Command.CommitSmartEnglishCandidateOrMultiTap,
        PhysicalT9KeyFlow.Command.CommitHighlightedReading,
        is PhysicalT9KeyFlow.Command.CommitBottomCandidate,
        is PhysicalT9KeyFlow.Command.CommitHanziShortcut,
        PhysicalT9KeyFlow.Command.CommitChineseCodePreview -> true
        else -> false
    }

    private fun PhysicalT9KeyFlow.Command.rendersCandidateSynchronously(): Boolean = when (this) {
        is PhysicalT9KeyFlow.Command.MoveCandidateFocus,
        is PhysicalT9KeyFlow.Command.MoveHighlightedReading -> true
        else -> false
    }
}

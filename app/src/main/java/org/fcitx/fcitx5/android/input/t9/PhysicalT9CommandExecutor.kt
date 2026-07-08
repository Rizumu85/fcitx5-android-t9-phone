/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

internal class PhysicalT9CommandExecutor(
    private val host: PhysicalT9KeyHandler.Host
) {
    fun execute(
        commands: List<PhysicalT9KeyFlow.Command>,
        input: PhysicalT9KeyHandler.KeyInput
    ) {
        commands.forEach { command -> execute(command, input) }
    }

    private fun execute(
        command: PhysicalT9KeyFlow.Command,
        input: PhysicalT9KeyHandler.KeyInput
    ) {
        when (command) {
            is PhysicalT9KeyFlow.Command.SetPendingPunctuationOneKeyDeferred ->
                host.setPendingPunctuationOneKeyDeferred(command.value)
            is PhysicalT9KeyFlow.Command.CommitPendingPunctuationShortcut ->
                host.commitPendingPunctuationShortcut(command.keyCode)
            PhysicalT9KeyFlow.Command.CommitPendingPunctuation ->
                host.commitPendingPunctuation()
            PhysicalT9KeyFlow.Command.CancelPendingPunctuation ->
                host.cancelPendingPunctuation()
            PhysicalT9KeyFlow.Command.HandleChinesePunctuationKey ->
                host.handleChinesePunctuationKey()
            PhysicalT9KeyFlow.Command.CancelMultiTapChar ->
                host.cancelMultiTapChar()
            PhysicalT9KeyFlow.Command.ShowSmartEnglishPunctuationCandidates ->
                host.showSmartEnglishPunctuationCandidates()
            is PhysicalT9KeyFlow.Command.HandleMultiTapKey ->
                host.handleMultiTapKey(command.keyCode)
            PhysicalT9KeyFlow.Command.CommitMultiTapChar ->
                host.commitMultiTapChar()
            is PhysicalT9KeyFlow.Command.CommitSmartEnglishShortcut ->
                host.commitSmartEnglishShortcut(command.keyCode)
            is PhysicalT9KeyFlow.Command.CommitSmartEnglishCandidate ->
                host.commitSmartEnglishCandidate(
                    appendSpace = command.appendSpace,
                    continuePrediction = command.continuePrediction
                )
            PhysicalT9KeyFlow.Command.CommitSmartEnglishCandidateOrMultiTap -> {
                if (!host.commitSmartEnglishCandidate()) {
                    host.commitMultiTapChar()
                }
            }
            PhysicalT9KeyFlow.Command.CommitEnglishPendingOrSpace -> {
                // English 0 keeps old "space when idle" behavior, but a smart candidate owns its trailing space.
                if (!host.commitSmartEnglishCandidate()) {
                    host.commitMultiTapChar()
                    host.commitPendingPunctuation()
                    host.commitText(" ")
                }
            }
            PhysicalT9KeyFlow.Command.CommitEnglishPendingOrReturn -> {
                val hadPendingChar =
                    host.commitSmartEnglishCandidate() ||
                        host.commitMultiTapChar() ||
                        host.commitPendingPunctuation()
                if (!hadPendingChar) {
                    host.handleReturnKey()
                }
            }
            is PhysicalT9KeyFlow.Command.AppendSmartEnglishDigit ->
                host.appendSmartEnglishDigit(command.digit)
            PhysicalT9KeyFlow.Command.ResetSmartEnglishT9 ->
                host.resetSmartEnglishT9()
            PhysicalT9KeyFlow.Command.FlushEnglishLearningWord ->
                host.flushEnglishLearningWord()
            is PhysicalT9KeyFlow.Command.MoveBottomCandidate -> {
                val moved = host.moveHighlightedBottomCandidate(command.delta)
                if (!moved) {
                    command.fallbackSmartEnglishDelta?.let { host.moveSmartEnglishCandidate(it) }
                }
            }
            is PhysicalT9KeyFlow.Command.OffsetBottomCandidatePage ->
                host.offsetBottomCandidatePage(command.delta)
            is PhysicalT9KeyFlow.Command.MoveCandidateFocus ->
                host.moveCandidateFocus(command.focus)
            is PhysicalT9KeyFlow.Command.MoveHighlightedPinyin ->
                host.moveHighlightedPinyin(command.delta)
            PhysicalT9KeyFlow.Command.CommitHighlightedPinyin ->
                host.commitHighlightedPinyin()
            is PhysicalT9KeyFlow.Command.CommitBottomCandidate -> {
                host.commitHighlightedBottomCandidate() ||
                    when (command.fallback) {
                        PhysicalT9KeyFlow.BottomCandidateFallback.NONE -> false
                        PhysicalT9KeyFlow.BottomCandidateFallback.PENDING_PUNCTUATION ->
                            host.commitPendingPunctuation()
                        PhysicalT9KeyFlow.BottomCandidateFallback.SMART_ENGLISH ->
                            host.commitSmartEnglishCandidate()
                    }
            }
            is PhysicalT9KeyFlow.Command.SmartEnglishDelete ->
                if (command.hasPendingPunctuation) {
                    host.cancelPendingPunctuation()
                } else {
                    host.smartEnglishBackspace()
                }
            is PhysicalT9KeyFlow.Command.CommitPendingPunctuationShortcutOrText ->
                if (!host.commitPendingPunctuationShortcut(command.keyCode)) {
                    host.cancelPendingPunctuation()
                    host.commitText(command.text)
                }
            is PhysicalT9KeyFlow.Command.CommitText ->
                host.commitText(command.text)
            is PhysicalT9KeyFlow.Command.CommitHanziShortcut ->
                host.commitHanziShortcut(command.keyCode)
            is PhysicalT9KeyFlow.Command.ForwardChineseT9KeyShortPress ->
                host.forwardChineseT9KeyShortPress(command.keyCode, input)
            PhysicalT9KeyFlow.Command.ForwardChineseT9SeparatorShortPress ->
                host.forwardChineseT9SeparatorShortPress()
            PhysicalT9KeyFlow.Command.ForwardChineseComposingPoundShortPress ->
                host.forwardChineseT9KeyShortPress(KeyEvent.KEYCODE_POUND, input)
            PhysicalT9KeyFlow.Command.TogglePendingPunctuationSet ->
                host.togglePendingPunctuationSet()
            PhysicalT9KeyFlow.Command.HandleEnglishStarShortPress ->
                host.handleEnglishStarShortPress()
            PhysicalT9KeyFlow.Command.HandleEnglishStarLongPress ->
                host.handleEnglishStarLongPress()
            PhysicalT9KeyFlow.Command.HandleReturnKey ->
                host.handleReturnKey()
            PhysicalT9KeyFlow.Command.SwitchToNextMode ->
                host.switchToNextMode()
            PhysicalT9KeyFlow.Command.DiscardChineseCompositionForModeSwitch ->
                host.discardChineseCompositionForModeSwitch()
            is PhysicalT9KeyFlow.Command.CommitNumberOperatorForKey ->
                host.commitNumberOperatorForKey(command.keyCode, command.fallbackDigit)
            PhysicalT9KeyFlow.Command.ShowNumberOperatorHintPanel ->
                host.showNumberOperatorHintPanel()
            PhysicalT9KeyFlow.Command.CommitLiteralStarInCurrentChineseState ->
                host.commitLiteralStarInCurrentChineseState()
        }
    }
}

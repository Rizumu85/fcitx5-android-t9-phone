/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

class PhysicalT9KeyHandler(private val host: Host) {

    enum class Mode {
        CHINESE,
        ENGLISH,
        NUMBER
    }

    enum class CandidateFocus {
        TOP,
        BOTTOM
    }

    enum class PunctuationSet {
        CHINESE,
        ENGLISH
    }

    data class KeyResult(
        val handled: Boolean,
        val consumedKeyUp: Int? = null
    )

    data class KeyInput(
        val keyCode: Int,
        val action: Int,
        val repeatCount: Int,
        val downTime: Long,
        val eventTime: Long,
        val metaState: Int = 0,
        val deviceId: Int = 0,
        val scanCode: Int = 0,
        val flags: Int = 0,
        val source: Int = 0
    ) {
        companion object {
            fun from(keyCode: Int, event: KeyEvent): KeyInput = KeyInput(
                keyCode = keyCode,
                action = event.action,
                repeatCount = event.repeatCount,
                downTime = event.downTime,
                eventTime = event.eventTime,
                metaState = event.metaState,
                deviceId = event.deviceId,
                scanCode = event.scanCode,
                flags = event.flags,
                source = event.source
            )
        }
    }

    interface Host {
        val isInInputMode: Boolean
        val mode: Mode
        val isSmartEnglishActive: Boolean
        val chineseComposing: Boolean
        val compositionKeyCount: Int
        val hasPendingPunctuation: Boolean
        val pendingPunctuationOneKeyDeferred: Boolean
        val pendingPunctuationSet: PunctuationSet
        val hasSmartEnglishDigits: Boolean
        val hasSmartEnglishCandidates: Boolean
        val hasMultiTapPendingChar: Boolean
        val hasTopPinyinCandidates: Boolean
        val hasBottomCandidateRow: Boolean
        val candidateFocus: CandidateFocus

        fun keyHeldPastLongPressDelay(input: KeyInput): Boolean
        fun setPendingPunctuationOneKeyDeferred(value: Boolean)
        fun commitPendingPunctuationShortcut(keyCode: Int): Boolean
        fun commitHanziShortcut(keyCode: Int): Boolean
        fun commitSmartEnglishShortcut(keyCode: Int): Boolean
        fun commitPendingPunctuation(): Boolean
        fun cancelPendingPunctuation(): Boolean
        fun handleChinesePunctuationKey(): Boolean
        fun togglePendingPunctuationSet(): Boolean
        fun switchToNextMode()
        fun commitText(text: String)
        fun commitNumberOperatorForKey(keyCode: Int, fallbackDigit: Int): Boolean
        fun showNumberOperatorHintPanel()
        fun commitLiteralStarInCurrentChineseState()
        fun handleEnglishStarShortPress()
        fun handleEnglishStarLongPress()
        fun handleMultiTapKey(keyCode: Int): Boolean
        fun commitMultiTapChar(): Boolean
        fun cancelMultiTapChar()
        fun showSmartEnglishPunctuationCandidates()
        fun appendSmartEnglishDigit(digit: Int)
        fun resetSmartEnglishT9()
        fun commitSmartEnglishCandidate(
            appendSpace: Boolean = true,
            continuePrediction: Boolean = appendSpace
        ): Boolean
        fun moveSmartEnglishCandidate(delta: Int): Boolean
        fun smartEnglishBackspace(): Boolean
        fun flushEnglishLearningWord()
        fun handleReturnKey()
        fun forwardChineseT9KeyShortPress(keyCode: Int, input: KeyInput): Boolean
        fun forwardChineseT9SeparatorShortPress(): Boolean
        fun moveCandidateFocus(focus: CandidateFocus)
        fun moveHighlightedPinyin(delta: Int): Boolean
        fun moveHighlightedBottomCandidate(delta: Int): Boolean
        fun offsetBottomCandidatePage(delta: Int): Boolean
        fun commitHighlightedPinyin(): Boolean
        fun commitHighlightedBottomCandidate(): Boolean
    }

    private val keyFlow = PhysicalT9KeyFlow()

    fun resetSmartEnglishPendingDigit() = keyFlow.resetSmartEnglishPendingDigit()

    fun handleKeyDown(keyCode: Int, event: KeyEvent): KeyResult =
        handleKeyDown(KeyInput.from(keyCode, event))

    fun handleKeyDown(input: KeyInput): KeyResult = T9ResponsivenessTrace.measure("PhysicalT9KeyHandler.keyDown") {
        if (!host.isInInputMode) return KeyResult(handled = false)
        if (input.action != KeyEvent.ACTION_DOWN) return KeyResult(handled = false)

        handleCommandKeyFlow(input).takeIf { it.handled }?.let { return it }
        return KeyResult(handled = false)
    }

    fun handleKeyUp(keyCode: Int, event: KeyEvent): KeyResult =
        handleKeyUp(KeyInput.from(keyCode, event))

    fun handleKeyUp(input: KeyInput): KeyResult = T9ResponsivenessTrace.measure("PhysicalT9KeyHandler.keyUp") {
        if (!host.isInInputMode) return KeyResult(handled = false)
        handleCommandKeyFlow(input).takeIf { it.handled }?.let { return it }
        return KeyResult(handled = false)
    }

    private fun handleCommandKeyFlow(input: KeyInput): KeyResult {
        val decision = keyFlow.handle(input, physicalKeyFlowState(input)) ?: return KeyResult(handled = false)
        executePhysicalKeyFlowCommands(decision.commands, input)
        return KeyResult(
            handled = decision.handled,
            consumedKeyUp = decision.consumedKeyUp
        )
    }

    private fun physicalKeyFlowState(input: KeyInput): PhysicalT9KeyFlow.State =
        PhysicalT9KeyFlow.State(
            mode = host.mode,
            isSmartEnglishActive = host.isSmartEnglishActive,
            chineseComposing = host.chineseComposing,
            compositionKeyCount = host.compositionKeyCount,
            hasPendingPunctuation = host.hasPendingPunctuation,
            pendingPunctuationOneKeyDeferred = host.pendingPunctuationOneKeyDeferred,
            pendingPunctuationSet = host.pendingPunctuationSet,
            hasSmartEnglishDigits = host.hasSmartEnglishDigits,
            hasSmartEnglishCandidates = host.hasSmartEnglishCandidates,
            hasMultiTapPendingChar = host.hasMultiTapPendingChar,
            hasTopPinyinCandidates = host.hasTopPinyinCandidates,
            hasBottomCandidateRow = host.hasBottomCandidateRow,
            candidateFocus = host.candidateFocus,
            heldPastLongPressDelay = host.keyHeldPastLongPressDelay(input)
        )

    private fun executePhysicalKeyFlowCommands(
        commands: List<PhysicalT9KeyFlow.Command>,
        input: KeyInput
    ) {
        commands.forEach { command ->
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
                is PhysicalT9KeyFlow.Command.CommitNumberOperatorForKey ->
                    host.commitNumberOperatorForKey(command.keyCode, command.fallbackDigit)
                PhysicalT9KeyFlow.Command.ShowNumberOperatorHintPanel ->
                    host.showNumberOperatorHintPanel()
                PhysicalT9KeyFlow.Command.CommitLiteralStarInCurrentChineseState ->
                    host.commitLiteralStarInCurrentChineseState()
            }
        }
    }

}

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

    private val digitLongPressFlags = BooleanArray(KeyEvent.KEYCODE_STAR + 1)
    private val keyFlow = PhysicalT9KeyFlow()
    private var poundLongPressTriggered = false

    private fun isDigitLongPressFlagSet(keyCode: Int): Boolean =
        digitLongPressFlags.getOrNull(keyCode) == true

    private fun setDigitLongPressFlag(keyCode: Int, value: Boolean) {
        if (keyCode in digitLongPressFlags.indices) {
            digitLongPressFlags[keyCode] = value
        }
    }

    fun resetSmartEnglishPendingDigit() = keyFlow.resetSmartEnglishPendingDigit()

    fun handleKeyDown(keyCode: Int, event: KeyEvent): KeyResult =
        handleKeyDown(KeyInput.from(keyCode, event))

    fun handleKeyDown(input: KeyInput): KeyResult = T9ResponsivenessTrace.measure("PhysicalT9KeyHandler.keyDown") {
        val keyCode = input.keyCode
        if (!host.isInInputMode) return KeyResult(handled = false)
        if (input.action != KeyEvent.ACTION_DOWN) return KeyResult(handled = false)

        handleCommandKeyFlow(input).takeIf { it.handled }?.let { return it }
        handleT9SpecialKeyDown(keyCode, input).takeIf { it.handled }?.let { return it }
        return KeyResult(handled = false)
    }

    fun handleKeyUp(keyCode: Int, event: KeyEvent): KeyResult =
        handleKeyUp(KeyInput.from(keyCode, event))

    fun handleKeyUp(input: KeyInput): KeyResult = T9ResponsivenessTrace.measure("PhysicalT9KeyHandler.keyUp") {
        if (!host.isInInputMode) return KeyResult(handled = false)
        handleCommandKeyFlow(input).takeIf { it.handled }?.let { return it }
        return handleT9SpecialKeyUp(input.keyCode, input)
    }

    private fun handleCommandKeyFlow(input: KeyInput): KeyResult {
        val decision = keyFlow.handle(input, physicalKeyFlowState(input)) ?: return KeyResult(handled = false)
        executePhysicalKeyFlowCommands(decision.commands)
        return KeyResult(
            handled = decision.handled,
            consumedKeyUp = decision.consumedKeyUp
        )
    }

    private fun physicalKeyFlowState(input: KeyInput): PhysicalT9KeyFlow.State =
        PhysicalT9KeyFlow.State(
            mode = host.mode,
            isSmartEnglishActive = host.isSmartEnglishActive,
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

    private fun executePhysicalKeyFlowCommands(commands: List<PhysicalT9KeyFlow.Command>) {
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

    private fun consumePhysicalLongPressIfReady(keyCode: Int, input: KeyInput): Boolean {
        if (isDigitLongPressFlagSet(keyCode)) return false
        if (!host.keyHeldPastLongPressDelay(input)) return false
        setDigitLongPressFlag(keyCode, true)
        return true
    }

    private fun consumePoundLongPressIfReady(input: KeyInput): Boolean {
        if (poundLongPressTriggered) return false
        if (!host.keyHeldPastLongPressDelay(input)) return false
        poundLongPressTriggered = true
        return true
    }

    private fun handleT9SpecialKeyDown(keyCode: Int, input: KeyInput): KeyResult {
        if (host.mode != Mode.CHINESE) return KeyResult(handled = false)

        return when {
            keyCode == KeyEvent.KEYCODE_POUND -> {
                if (host.mode == Mode.CHINESE && host.chineseComposing) {
                    KeyResult(handled = false)
                } else {
                    if (input.repeatCount == 0) {
                        poundLongPressTriggered = false
                    } else if (consumePoundLongPressIfReady(input)) {
                        host.switchToNextMode()
                    }
                    KeyResult(handled = true)
                }
            }
            PhysicalT9KeyPolicy.t9Digit(keyCode) != null ->
                KeyResult(handled = handleDigitKeyDown(keyCode, input))
            keyCode == KeyEvent.KEYCODE_STAR ->
                KeyResult(handled = handleStarKeyDown(keyCode, input))
            PhysicalT9KeyPolicy.focusKey(keyCode) == PhysicalT9KeyPolicy.FocusKey.OK ->
                KeyResult(handled = false)
            else -> KeyResult(handled = false)
        }
    }

    private fun handleDigitKeyDown(keyCode: Int, input: KeyInput): Boolean {
        val digit = PhysicalT9KeyPolicy.t9Digit(keyCode) ?: return false
        return when (host.mode) {
            Mode.ENGLISH -> false
            Mode.CHINESE -> handleChineseDigitKeyDown(keyCode, input, digit)
            Mode.NUMBER -> false
        }
    }

    private fun handleChineseDigitKeyDown(keyCode: Int, input: KeyInput, digit: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_1 && host.compositionKeyCount > 0) {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(keyCode, false)
            } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                host.commitHanziShortcut(keyCode)
            }
            return true
        }
        if (PhysicalT9KeyPolicy.isPredictiveDigitKey(keyCode)) {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(keyCode, false)
            } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                if (host.compositionKeyCount > 0) {
                    host.commitHanziShortcut(keyCode)
                } else {
                    host.commitText(digit.toString())
                }
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_0 && host.compositionKeyCount > 0) {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(keyCode, false)
            } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                host.commitHanziShortcut(keyCode)
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_1 &&
            input.repeatCount == 0 &&
            !host.hasPendingPunctuation &&
            host.compositionKeyCount > 0
        ) {
            setDigitLongPressFlag(keyCode, false)
            return true
        }
        if (host.chineseComposing) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_0 -> {
                if (input.repeatCount == 0) {
                    setDigitLongPressFlag(keyCode, false)
                } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                    host.commitText("0")
                }
                true
            }
            KeyEvent.KEYCODE_1 -> {
                if (input.repeatCount == 0) {
                    setDigitLongPressFlag(keyCode, false)
                    host.setPendingPunctuationOneKeyDeferred(true)
                } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                    host.setPendingPunctuationOneKeyDeferred(false)
                    host.cancelPendingPunctuation()
                    host.commitText("1")
                }
                true
            }
            else -> {
                if (input.repeatCount == 0) {
                    setDigitLongPressFlag(keyCode, false)
                    false
                } else {
                    if (consumePhysicalLongPressIfReady(keyCode, input)) {
                        host.commitText(digit.toString())
                    }
                    true
                }
            }
        }
    }

    private fun handleStarKeyDown(keyCode: Int, input: KeyInput): Boolean = when (host.mode) {
        Mode.ENGLISH -> false
        Mode.CHINESE -> {
            if (host.hasPendingPunctuation) {
                true
            } else {
                if (input.repeatCount == 0) {
                    host.commitLiteralStarInCurrentChineseState()
                }
                true
            }
        }
        Mode.NUMBER -> false
    }

    private fun handleT9SpecialKeyUp(keyCode: Int, input: KeyInput): KeyResult {
        if (host.mode != Mode.CHINESE) return KeyResult(handled = false)
        return when {
            keyCode == KeyEvent.KEYCODE_POUND -> {
                if (host.mode == Mode.CHINESE && host.chineseComposing) {
                    KeyResult(handled = false)
                } else {
                    if (!poundLongPressTriggered) {
                        val hadPendingChar = when {
                            host.hasPendingPunctuation -> host.commitPendingPunctuation()
                            else ->
                                host.commitSmartEnglishCandidate() ||
                                    host.commitMultiTapChar() ||
                                    host.commitPendingPunctuation()
                        }
                        if (!hadPendingChar) {
                            host.handleReturnKey()
                        }
                    }
                    poundLongPressTriggered = false
                    KeyResult(handled = true)
                }
            }
            keyCode == KeyEvent.KEYCODE_0 -> KeyResult(handled = handleZeroKeyUp(keyCode, input))
            PhysicalT9KeyPolicy.isPredictiveDigitKey(keyCode) ->
                KeyResult(handled = handlePredictiveDigitKeyUp(keyCode, input))
            keyCode == KeyEvent.KEYCODE_1 -> KeyResult(handled = handleOneKeyUp(keyCode))
            keyCode == KeyEvent.KEYCODE_STAR -> KeyResult(handled = handleStarKeyUp(keyCode))
            PhysicalT9KeyPolicy.focusKey(keyCode) == PhysicalT9KeyPolicy.FocusKey.OK ->
                KeyResult(handled = false)
            else -> KeyResult(handled = false)
        }
    }

    private fun handleZeroKeyUp(keyCode: Int, input: KeyInput): Boolean = when (host.mode) {
        Mode.CHINESE -> handleChineseDigitKeyUp(keyCode, input)
        Mode.ENGLISH -> false
        Mode.NUMBER -> false
    }

    private fun handlePredictiveDigitKeyUp(keyCode: Int, input: KeyInput): Boolean = when (host.mode) {
        Mode.ENGLISH -> false
        Mode.CHINESE -> handleChineseDigitKeyUp(keyCode, input)
        Mode.NUMBER -> false
    }

    private fun handleOneKeyUp(keyCode: Int): Boolean = when (host.mode) {
        Mode.ENGLISH -> false
        Mode.CHINESE -> handleChineseDigitKeyUp(keyCode, null)
        Mode.NUMBER -> false
    }

    private fun handleStarKeyUp(keyCode: Int): Boolean = when (host.mode) {
        Mode.ENGLISH -> false
        Mode.CHINESE -> {
            host.togglePendingPunctuationSet()
            true
        }
        Mode.NUMBER -> false
    }

    private fun handleChineseDigitKeyUp(keyCode: Int, input: KeyInput?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_0 -> {
                if (isDigitLongPressFlagSet(keyCode)) {
                    setDigitLongPressFlag(keyCode, false)
                    true
                } else if (host.compositionKeyCount > 0) {
                    val committed = when (host.candidateFocus) {
                        CandidateFocus.TOP -> host.commitHighlightedPinyin()
                        CandidateFocus.BOTTOM -> host.commitHighlightedBottomCandidate()
                    }
                    setDigitLongPressFlag(keyCode, false)
                    committed
                } else if (host.chineseComposing) {
                    false
                } else {
                    host.commitMultiTapChar()
                    host.commitPendingPunctuation()
                    host.commitText(" ")
                    setDigitLongPressFlag(keyCode, false)
                    true
                }
            }
            KeyEvent.KEYCODE_1 -> {
                if (isDigitLongPressFlagSet(keyCode)) {
                    setDigitLongPressFlag(keyCode, false)
                    true
                } else if (host.compositionKeyCount > 0) {
                    host.forwardChineseT9SeparatorShortPress()
                    true
                } else if (host.pendingPunctuationOneKeyDeferred) {
                    host.handleChinesePunctuationKey()
                    setDigitLongPressFlag(keyCode, false)
                    host.setPendingPunctuationOneKeyDeferred(false)
                    true
                } else {
                    true
                }
            }
            else -> if (PhysicalT9KeyPolicy.isPredictiveDigitKey(keyCode)) {
                if (isDigitLongPressFlagSet(keyCode)) {
                    setDigitLongPressFlag(keyCode, false)
                    true
                } else {
                    input?.let { host.forwardChineseT9KeyShortPress(keyCode, it) } ?: false
                }
            } else {
                false
            }
        }
    }
}

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
        val hasMultiTapPendingChar: Boolean
        val hasTopPinyinCandidates: Boolean
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
        fun deferSmartEnglishPunctuationKey()
        fun showSmartEnglishPunctuationCandidates()
        fun appendSmartEnglishDigit(digit: Int)
        fun resetSmartEnglishT9()
        fun commitSmartEnglishCandidate(): Boolean
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
    private val effectPlanner = T9KeyEffectPlanner()
    private var poundLongPressTriggered = false
    private var pendingSmartEnglishDigitKeyCode: Int? = null
    private var pendingSmartEnglishDigit = -1

    private fun isDigitLongPressFlagSet(keyCode: Int): Boolean =
        digitLongPressFlags.getOrNull(keyCode) == true

    private fun setDigitLongPressFlag(keyCode: Int, value: Boolean) {
        if (keyCode in digitLongPressFlags.indices) {
            digitLongPressFlags[keyCode] = value
        }
    }

    fun resetSmartEnglishPendingDigit() {
        pendingSmartEnglishDigitKeyCode = null
        pendingSmartEnglishDigit = -1
    }

    fun handleKeyDown(keyCode: Int, event: KeyEvent): KeyResult =
        handleKeyDown(KeyInput.from(keyCode, event))

    fun handleKeyDown(input: KeyInput): KeyResult = T9ResponsivenessTrace.measure("PhysicalT9KeyHandler.keyDown") {
        val keyCode = input.keyCode
        if (!host.isInInputMode) return KeyResult(handled = false)
        if (input.action != KeyEvent.ACTION_DOWN) return KeyResult(handled = false)

        if (host.hasPendingPunctuation &&
            !PhysicalT9KeyPolicy.isPendingPunctuationControlKey(keyCode)
        ) {
            host.commitPendingPunctuation()
        }

        handleT9SpecialKeyDown(keyCode, input).takeIf { it.handled }?.let { return it }
        handleSmartEnglishNavigationKeyDown(keyCode, input).takeIf { it.handled }?.let { return it }
        handleEnglishDeleteKeyDown(keyCode, input).takeIf { it.handled }?.let { return it }
        handleChineseCandidateFocusNavigation(keyCode, input).takeIf { it.handled }?.let { return it }
        return KeyResult(handled = false)
    }

    fun handleKeyUp(keyCode: Int, event: KeyEvent): KeyResult =
        handleKeyUp(KeyInput.from(keyCode, event))

    fun handleKeyUp(input: KeyInput): KeyResult = T9ResponsivenessTrace.measure("PhysicalT9KeyHandler.keyUp") {
        if (!host.isInInputMode) return KeyResult(handled = false)
        return handleT9SpecialKeyUp(input.keyCode, input)
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
        if ((host.mode == Mode.CHINESE || host.isSmartEnglishActive) && host.hasPendingPunctuation) {
            when (keyCode) {
                KeyEvent.KEYCODE_1 -> {
                    if (input.repeatCount == 0) {
                        setDigitLongPressFlag(keyCode, false)
                        if (host.mode == Mode.CHINESE) {
                            host.setPendingPunctuationOneKeyDeferred(true)
                        }
                    } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                        host.setPendingPunctuationOneKeyDeferred(false)
                        host.commitPendingPunctuationShortcut(keyCode)
                    }
                    return KeyResult(handled = true)
                }
                KeyEvent.KEYCODE_STAR -> return KeyResult(handled = true)
                KeyEvent.KEYCODE_POUND -> {
                    if (input.repeatCount == 0) {
                        poundLongPressTriggered = false
                    } else if (consumePoundLongPressIfReady(input)) {
                        host.commitPendingPunctuation()
                        host.switchToNextMode()
                    }
                    return KeyResult(handled = true)
                }
                KeyEvent.KEYCODE_0 -> {
                    if (input.repeatCount == 0) {
                        setDigitLongPressFlag(keyCode, false)
                    } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                        if (host.commitPendingPunctuationShortcut(keyCode)) return KeyResult(handled = true)
                        host.cancelPendingPunctuation()
                        host.commitText("0")
                    }
                    return KeyResult(handled = true)
                }
                else -> if (PhysicalT9KeyPolicy.isPredictiveDigitKey(keyCode)) {
                    if (input.repeatCount == 0) {
                        setDigitLongPressFlag(keyCode, false)
                    } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                        host.commitPendingPunctuationShortcut(keyCode)
                    }
                    return KeyResult(handled = true)
                }
            }
        }

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
            PhysicalT9KeyPolicy.focusKey(keyCode) == PhysicalT9KeyPolicy.FocusKey.OK -> {
                if (host.mode == Mode.ENGLISH && host.hasMultiTapPendingChar) {
                    if (input.repeatCount == 0) {
                        host.commitMultiTapChar()
                    }
                    KeyResult(handled = true)
                } else {
                    KeyResult(handled = false)
                }
            }
            else -> KeyResult(handled = false)
        }
    }

    private fun handleDigitKeyDown(keyCode: Int, input: KeyInput): Boolean {
        val digit = PhysicalT9KeyPolicy.t9Digit(keyCode) ?: return false
        return when (host.mode) {
            Mode.NUMBER -> {
                if (input.repeatCount == 0) {
                    setDigitLongPressFlag(keyCode, false)
                } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                    host.commitNumberOperatorForKey(keyCode, digit)
                }
                true
            }
            Mode.ENGLISH -> handleEnglishDigitKeyDown(keyCode, input, digit)
            Mode.CHINESE -> handleChineseDigitKeyDown(keyCode, input, digit)
        }
    }

    private fun handleEnglishDigitKeyDown(keyCode: Int, input: KeyInput, digit: Int): Boolean {
        if (handleSmartEnglishDigitKeyDown(keyCode, input, digit)) return true
        return when {
            PhysicalT9KeyPolicy.isPredictiveDigitKey(keyCode) -> {
                if (input.repeatCount == 0) {
                    setDigitLongPressFlag(keyCode, false)
                    host.handleMultiTapKey(keyCode)
                } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                    host.cancelMultiTapChar()
                    host.commitText(digit.toString())
                }
                true
            }
            keyCode == KeyEvent.KEYCODE_0 -> {
                if (input.repeatCount == 0) {
                    setDigitLongPressFlag(keyCode, false)
                } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                    if (host.hasSmartEnglishDigits && host.commitSmartEnglishShortcut(keyCode)) return true
                    host.cancelMultiTapChar()
                    host.commitText("0")
                }
                true
            }
            keyCode == KeyEvent.KEYCODE_1 -> {
                if (input.repeatCount == 0) {
                    setDigitLongPressFlag(keyCode, false)
                    if (host.isSmartEnglishActive) {
                        host.deferSmartEnglishPunctuationKey()
                        return true
                    }
                    if (host.hasSmartEnglishDigits) return true
                    host.handleMultiTapKey(keyCode)
                } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                    if (host.hasSmartEnglishDigits && host.commitSmartEnglishShortcut(keyCode)) return true
                    host.cancelPendingPunctuation()
                    host.cancelMultiTapChar()
                    host.commitText("1")
                }
                true
            }
            else -> false
        }
    }

    private fun handleSmartEnglishDigitKeyDown(keyCode: Int, input: KeyInput, digit: Int): Boolean {
        if (!host.isSmartEnglishActive) return false
        if (!PhysicalT9KeyPolicy.isPredictiveDigitKey(keyCode)) return false
        if (input.repeatCount == 0) {
            setDigitLongPressFlag(keyCode, false)
            pendingSmartEnglishDigitKeyCode = keyCode
            pendingSmartEnglishDigit = digit
            return true
        }
        if (consumePhysicalLongPressIfReady(keyCode, input)) {
            resetSmartEnglishPendingDigit()
            if (host.hasSmartEnglishDigits && host.commitSmartEnglishShortcut(keyCode)) return true
            host.resetSmartEnglishT9()
            host.commitText(digit.toString())
            host.flushEnglishLearningWord()
        }
        return true
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
        Mode.ENGLISH -> {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(keyCode, false)
            } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                host.handleEnglishStarLongPress()
            }
            true
        }
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
        Mode.NUMBER -> {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(keyCode, false)
            } else if (consumePhysicalLongPressIfReady(keyCode, input)) {
                host.showNumberOperatorHintPanel()
            }
            true
        }
    }

    private fun handleSmartEnglishNavigationKeyDown(keyCode: Int, input: KeyInput): KeyResult {
        val effect = effectPlanner.planSmartEnglishNavigationKeyDown(input, t9EffectSnapshot())
        return executeT9Effect(effect, keyCode)
    }

    private fun handleEnglishDeleteKeyDown(keyCode: Int, input: KeyInput): KeyResult {
        val effect = effectPlanner.planEnglishDeleteKeyDown(input, t9EffectSnapshot())
        return executeT9Effect(effect, keyCode)
    }

    private fun handleChineseCandidateFocusNavigation(keyCode: Int, input: KeyInput): KeyResult {
        val effect = effectPlanner.planChineseCandidateFocusNavigation(input, t9EffectSnapshot())
        return executeT9Effect(effect, keyCode)
    }

    private fun t9EffectSnapshot(): T9KeyEffectPlanner.Snapshot = T9KeyEffectPlanner.Snapshot(
        mode = host.mode,
        isSmartEnglishActive = host.isSmartEnglishActive,
        hasSmartEnglishDigits = host.hasSmartEnglishDigits,
        hasPendingPunctuation = host.hasPendingPunctuation,
        hasMultiTapPendingChar = host.hasMultiTapPendingChar,
        hasTopPinyinCandidates = host.hasTopPinyinCandidates,
        candidateFocus = host.candidateFocus
    )

    private fun executeT9Effect(effect: T9KeyEffectPlanner.Effect, keyCode: Int): KeyResult {
        val handled = when (effect) {
            T9KeyEffectPlanner.Effect.None -> false
            is T9KeyEffectPlanner.Effect.MoveBottomCandidate -> {
                val moved = host.moveHighlightedBottomCandidate(effect.delta)
                if (!moved) {
                    effect.fallbackSmartEnglishDelta?.let { host.moveSmartEnglishCandidate(it) }
                    effect.handledWhenPendingPunctuation && host.hasPendingPunctuation ||
                        effect.fallbackSmartEnglishDelta != null
                } else {
                    true
                }
            }
            is T9KeyEffectPlanner.Effect.OffsetBottomCandidatePage ->
                host.offsetBottomCandidatePage(effect.delta) ||
                    (effect.handledWhenPendingPunctuation && host.hasPendingPunctuation) ||
                    effect.alwaysHandled
            is T9KeyEffectPlanner.Effect.MoveCandidateFocus -> {
                host.moveCandidateFocus(effect.focus)
                true
            }
            is T9KeyEffectPlanner.Effect.MoveHighlightedPinyin ->
                host.moveHighlightedPinyin(effect.delta)
            T9KeyEffectPlanner.Effect.CommitHighlightedPinyin ->
                host.commitHighlightedPinyin()
            is T9KeyEffectPlanner.Effect.CommitHighlightedBottomCandidate ->
                host.commitHighlightedBottomCandidate() ||
                    (effect.handledWhenPendingPunctuation && host.hasPendingPunctuation)
            is T9KeyEffectPlanner.Effect.ConfirmSmartEnglishCandidate ->
                host.commitHighlightedBottomCandidate() || if (effect.hasPendingPunctuation) {
                    host.commitPendingPunctuation()
                } else {
                    host.commitSmartEnglishCandidate()
                }
            is T9KeyEffectPlanner.Effect.SmartEnglishDelete ->
                if (effect.hasPendingPunctuation) {
                    host.cancelPendingPunctuation()
                } else {
                    host.smartEnglishBackspace()
                }
            is T9KeyEffectPlanner.Effect.CancelMultiTapChar -> {
                host.cancelMultiTapChar()
                true
            }
            is T9KeyEffectPlanner.Effect.CancelPendingPunctuation ->
                host.cancelPendingPunctuation()
        }
        return KeyResult(
            handled = handled,
            consumedKeyUp = keyCode.takeIf { handled && effect.consumeKeyUp }
        )
    }

    private fun handleT9SpecialKeyUp(keyCode: Int, input: KeyInput): KeyResult {
        return when {
            keyCode == KeyEvent.KEYCODE_POUND -> {
                if (host.mode == Mode.CHINESE && host.hasPendingPunctuation) {
                    if (!poundLongPressTriggered) {
                        host.commitPendingPunctuation()
                    }
                    poundLongPressTriggered = false
                    KeyResult(handled = true)
                } else if (host.mode == Mode.CHINESE && host.chineseComposing) {
                    KeyResult(handled = false)
                } else {
                    if (!poundLongPressTriggered) {
                        val hadPendingChar =
                            host.commitSmartEnglishCandidate() ||
                                host.commitMultiTapChar() ||
                                host.commitPendingPunctuation()
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
                KeyResult(handled = host.mode == Mode.ENGLISH)
            else -> KeyResult(handled = false)
        }
    }

    private fun handleZeroKeyUp(keyCode: Int, input: KeyInput): Boolean = when (host.mode) {
        Mode.CHINESE -> handleChineseDigitKeyUp(keyCode, input)
        Mode.NUMBER -> {
            if (!isDigitLongPressFlagSet(keyCode)) {
                host.commitText("0")
            }
            setDigitLongPressFlag(keyCode, false)
            true
        }
        Mode.ENGLISH -> {
            if (!isDigitLongPressFlagSet(keyCode)) {
                val committedSmartWord = host.commitSmartEnglishCandidate()
                if (!committedSmartWord) {
                    host.commitMultiTapChar()
                }
                host.commitPendingPunctuation()
                host.commitText(" ")
                host.flushEnglishLearningWord()
            }
            setDigitLongPressFlag(keyCode, false)
            true
        }
    }

    private fun handlePredictiveDigitKeyUp(keyCode: Int, input: KeyInput): Boolean = when (host.mode) {
        Mode.ENGLISH -> {
            if (commitPendingSmartEnglishDigitKeyUp(keyCode)) return true
            if (host.hasPendingPunctuation) {
                if (!isDigitLongPressFlagSet(keyCode)) {
                    val digit = PhysicalT9KeyPolicy.t9Digit(keyCode) ?: return false
                    host.commitPendingPunctuation()
                    host.appendSmartEnglishDigit(digit)
                }
                setDigitLongPressFlag(keyCode, false)
                true
            } else {
                setDigitLongPressFlag(keyCode, false)
                true
            }
        }
        Mode.CHINESE -> handleChineseDigitKeyUp(keyCode, input)
        Mode.NUMBER -> {
            if (!isDigitLongPressFlagSet(keyCode)) {
                val digit = PhysicalT9KeyPolicy.t9Digit(keyCode) ?: return false
                host.commitText(digit.toString())
            }
            setDigitLongPressFlag(keyCode, false)
            true
        }
    }

    private fun commitPendingSmartEnglishDigitKeyUp(keyCode: Int): Boolean {
        if (!host.isSmartEnglishActive) return false
        if (pendingSmartEnglishDigitKeyCode != keyCode) return false
        val digit = pendingSmartEnglishDigit
        resetSmartEnglishPendingDigit()
        if (isDigitLongPressFlagSet(keyCode)) {
            setDigitLongPressFlag(keyCode, false)
            return true
        }
        if (digit in 2..9) {
            host.appendSmartEnglishDigit(digit)
        }
        setDigitLongPressFlag(keyCode, false)
        return true
    }

    private fun handleOneKeyUp(keyCode: Int): Boolean = when (host.mode) {
        Mode.ENGLISH -> {
            if (isDigitLongPressFlagSet(keyCode)) {
                setDigitLongPressFlag(keyCode, false)
                return true
            }
            if (host.pendingPunctuationOneKeyDeferred &&
                host.pendingPunctuationSet == PunctuationSet.ENGLISH
            ) {
                if (host.hasSmartEnglishDigits) {
                    host.commitSmartEnglishCandidate()
                }
                host.showSmartEnglishPunctuationCandidates()
                host.setPendingPunctuationOneKeyDeferred(false)
                return true
            }
            if (host.hasPendingPunctuation) return true
            if (host.hasSmartEnglishDigits) {
                host.commitSmartEnglishCandidate()
            }
            true
        }
        Mode.CHINESE -> handleChineseDigitKeyUp(keyCode, null)
        Mode.NUMBER -> {
            if (isDigitLongPressFlagSet(keyCode)) {
                setDigitLongPressFlag(keyCode, false)
            } else {
                host.commitText("1")
                setDigitLongPressFlag(keyCode, false)
            }
            true
        }
    }

    private fun handleStarKeyUp(keyCode: Int): Boolean = when (host.mode) {
        Mode.ENGLISH -> {
            if (host.hasPendingPunctuation && !isDigitLongPressFlagSet(keyCode)) {
                host.togglePendingPunctuationSet()
            } else if (!isDigitLongPressFlagSet(keyCode)) {
                host.handleEnglishStarShortPress()
            }
            setDigitLongPressFlag(keyCode, false)
            true
        }
        Mode.CHINESE -> {
            host.togglePendingPunctuationSet()
            true
        }
        Mode.NUMBER -> {
            if (!isDigitLongPressFlagSet(keyCode)) {
                host.commitLiteralStarInCurrentChineseState()
            }
            setDigitLongPressFlag(keyCode, false)
            true
        }
    }

    private fun handleChineseDigitKeyUp(keyCode: Int, input: KeyInput?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_0 -> {
                if (host.hasPendingPunctuation) {
                    if (!isDigitLongPressFlagSet(keyCode)) {
                        host.commitPendingPunctuation()
                        host.commitText(" ")
                    }
                    setDigitLongPressFlag(keyCode, false)
                    true
                } else if (isDigitLongPressFlagSet(keyCode)) {
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
                if (host.hasPendingPunctuation) {
                    if (!isDigitLongPressFlagSet(keyCode) && host.pendingPunctuationOneKeyDeferred) {
                        host.handleChinesePunctuationKey()
                    }
                    setDigitLongPressFlag(keyCode, false)
                    host.setPendingPunctuationOneKeyDeferred(false)
                    true
                } else if (isDigitLongPressFlagSet(keyCode)) {
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
                if (host.hasPendingPunctuation) {
                    if (!isDigitLongPressFlagSet(keyCode)) {
                        val digit = PhysicalT9KeyPolicy.t9Digit(keyCode) ?: return false
                        host.commitPendingPunctuation()
                        host.commitText(digit.toString())
                    }
                    setDigitLongPressFlag(keyCode, false)
                    true
                } else if (isDigitLongPressFlagSet(keyCode)) {
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

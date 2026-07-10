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
        val chineseScheme: ChineseT9Scheme
            get() = ChineseT9Scheme.PINYIN
        val isSmartEnglishActive: Boolean
        val chineseComposing: Boolean
        val compositionKeyCount: Int
        val hasPendingPunctuation: Boolean
        val hasSmartEnglishDigits: Boolean
        val hasSmartEnglishCandidates: Boolean
        val hasMultiTapPendingChar: Boolean
        val hasTopReadingCandidates: Boolean
        val hasBottomCandidateRow: Boolean
        val candidateFocus: CandidateFocus

        fun keyHeldPastLongPressDelay(input: KeyInput): Boolean
        fun commitPendingPunctuationShortcut(keyCode: Int): Boolean
        fun commitHanziShortcut(keyCode: Int): Boolean
        fun commitSmartEnglishShortcut(keyCode: Int): Boolean
        fun commitPendingPunctuation(): Boolean
        fun cancelPendingPunctuation(): Boolean
        fun showChinesePunctuationCandidates()
        fun showEnglishPunctuationCandidates()
        fun commitChineseCandidateAndShowPunctuation()
        fun togglePendingPunctuationSet(): Boolean
        fun switchToNextMode()
        fun commitText(text: String)
        fun commitNumberOperatorForKey(keyCode: Int, fallbackDigit: Int): Boolean
        fun showNumberOperatorHintPanel()
        fun commitLiteralStar()
        fun cycleEnglishCase()
        fun handleMultiTapKey(keyCode: Int): Boolean
        fun commitMultiTapChar(): Boolean
        fun cancelMultiTapChar()
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
        fun commitChineseCodePreview(): Boolean
        fun cycleChineseScheme(): Boolean
        fun forwardChineseT9KeyShortPress(keyCode: Int, input: KeyInput): Boolean
        fun forwardChineseT9SeparatorShortPress(): Boolean
        fun discardChineseCompositionForModeSwitch()
        fun moveCandidateFocus(focus: CandidateFocus)
        fun moveHighlightedReading(delta: Int): Boolean
        fun moveHighlightedBottomCandidate(delta: Int): Boolean
        fun offsetBottomCandidatePage(delta: Int): Boolean
        fun commitHighlightedReading(): Boolean
        fun commitHighlightedBottomCandidate(): Boolean
    }

    private val keyFlow = PhysicalT9KeyFlow()
    private val commandExecutor = PhysicalT9CommandExecutor(host)

    fun resetSmartEnglishPendingDigit() = keyFlow.resetSmartEnglishPendingDigit()

    fun handleKeyDown(keyCode: Int, event: KeyEvent): KeyResult =
        handleKeyDown(KeyInput.from(keyCode, event))

    fun handleKeyDown(input: KeyInput): KeyResult = T9ResponsivenessTrace.measure("PhysicalT9KeyHandler.keyDown") {
        if (!host.isInInputMode) return KeyResult(handled = false)
        if (input.action != KeyEvent.ACTION_DOWN) return KeyResult(handled = false)

        handleCommandKeyFlow(input).takeIf { it.handled || it.consumedKeyUp != null }?.let { return it }
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
        val state = physicalKeyFlowState(input)
        val decision = keyFlow.handle(input, state) ?: return KeyResult(handled = false)
        // A deferred key-down has no user-visible work yet. Starting only when commands exist
        // prevents long-press hold time from being misreported as input-processing latency.
        val traceId = decision.commands.takeIf {
            it.isNotEmpty() && (state.mode == Mode.CHINESE || state.isSmartEnglishActive)
        }
            ?.let {
                T9ResponsivenessTrace.beginInput(
                    path = state.tracePath(),
                    requiresSourceEvent = decision.commands.any { it.waitsForFcitxSourceEvent() }
                )
            }
        try {
            commandExecutor.execute(decision.commands, input)
        } finally {
            T9ResponsivenessTrace.markDecisionComplete(traceId)
        }
        return KeyResult(
            handled = decision.handled,
            consumedKeyUp = decision.consumedKeyUp
        )
    }

    private fun PhysicalT9KeyFlow.State.tracePath(): String = when (mode) {
        Mode.CHINESE -> "CHINESE/${chineseScheme.name}"
        Mode.ENGLISH -> if (isSmartEnglishActive) "SMART_ENGLISH" else "ENGLISH"
        Mode.NUMBER -> "NUMBER"
    }

    private fun PhysicalT9KeyFlow.Command.waitsForFcitxSourceEvent(): Boolean = when (this) {
        is PhysicalT9KeyFlow.Command.ForwardChineseT9KeyShortPress,
        PhysicalT9KeyFlow.Command.ForwardChineseT9SeparatorShortPress -> true
        // Local focus, paging, punctuation, and immediate-hide commands own their next UI frame;
        // waiting for an unrelated Rime event would strand those transactions.
        else -> false
    }

    private fun physicalKeyFlowState(input: KeyInput): PhysicalT9KeyFlow.State =
        PhysicalT9KeyFlow.State(
            mode = host.mode,
            isSmartEnglishActive = host.isSmartEnglishActive,
            chineseComposing = host.chineseComposing,
            compositionKeyCount = host.compositionKeyCount,
            hasPendingPunctuation = host.hasPendingPunctuation,
            hasSmartEnglishDigits = host.hasSmartEnglishDigits,
            hasSmartEnglishCandidates = host.hasSmartEnglishCandidates,
            hasMultiTapPendingChar = host.hasMultiTapPendingChar,
            hasTopReadingCandidates = host.hasTopReadingCandidates,
            hasBottomCandidateRow = host.hasBottomCandidateRow,
            candidateFocus = host.candidateFocus,
            heldPastLongPressDelay = host.keyHeldPastLongPressDelay(input),
            chineseScheme = host.chineseScheme
        )

}

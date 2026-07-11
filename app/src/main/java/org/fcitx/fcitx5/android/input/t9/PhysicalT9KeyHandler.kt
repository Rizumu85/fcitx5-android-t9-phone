/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

class PhysicalT9KeyHandler(
    private val host: Host,
    private val completeInputSurfaceFrame: (Long) -> Unit = {},
    private val completeCandidateFrame: (Long) -> Unit = {}
) {

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
        val idleLongZeroVoiceEnabled: Boolean

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
        fun switchToVoiceInput()
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
    private val stateCapture = PhysicalT9StateCapture(
        PhysicalT9StateCapture.Source(
            mode = host::mode,
            chineseScheme = host::chineseScheme,
            isSmartEnglishActive = host::isSmartEnglishActive,
            chineseComposing = host::chineseComposing,
            compositionKeyCount = host::compositionKeyCount,
            hasPendingPunctuation = host::hasPendingPunctuation,
            hasSmartEnglishDigits = host::hasSmartEnglishDigits,
            hasSmartEnglishCandidates = host::hasSmartEnglishCandidates,
            hasMultiTapPendingChar = host::hasMultiTapPendingChar,
            hasTopReadingCandidates = host::hasTopReadingCandidates,
            hasBottomCandidateRow = host::hasBottomCandidateRow,
            candidateFocus = host::candidateFocus,
            idleLongZeroVoiceEnabled = host::idleLongZeroVoiceEnabled,
            heldPastLongPressDelay = host::keyHeldPastLongPressDelay
        )
    )

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
        val state = stateCapture.capture(input)
        val decision = keyFlow.handle(input, state) ?: return KeyResult(handled = false)
        // First Chinese digits intentionally continue through the outer mapped-key route. They
        // still start a generation here, while command-free long-press deferrals do not.
        val forwardsThroughOuterRoute = state.mode == Mode.CHINESE &&
            !decision.handled &&
            decision.consumedKeyUp == input.keyCode
        val tracePlan = PhysicalT9TracePlanner.plan(state, decision, forwardsThroughOuterRoute)
        val traceId = tracePlan?.let {
            T9ResponsivenessTrace.beginInput(
                path = it.path,
                completionKind = it.completionKind,
                requiresSourceEvent = it.requiresSourceEvent
            )
        }
        T9ResponsivenessTrace.markDecisionComplete(traceId)
        try {
            commandExecutor.execute(decision.commands, input)
            T9ResponsivenessTrace.markEffectApplied(traceId)
            when (tracePlan?.completionKind) {
                T9ResponsivenessTrace.CompletionKind.EFFECT ->
                    T9ResponsivenessTrace.completeEffect(traceId)
                T9ResponsivenessTrace.CompletionKind.INPUT_SURFACE_FRAME ->
                    traceId?.let(completeInputSurfaceFrame)
                T9ResponsivenessTrace.CompletionKind.CANDIDATE_FRAME ->
                    if (tracePlan.candidateFrameIsSynchronous) {
                        traceId?.let(completeCandidateFrame)
                    }
                null -> Unit
            }
        } catch (error: Throwable) {
            T9ResponsivenessTrace.discardInput(traceId)
            throw error
        }
        return KeyResult(
            handled = decision.handled,
            consumedKeyUp = decision.consumedKeyUp
        )
    }

}

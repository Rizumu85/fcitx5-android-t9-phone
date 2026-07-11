/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

internal class PhysicalT9StateCapture(
    private val source: Source
) {
    data class Source(
        val mode: () -> PhysicalT9KeyHandler.Mode,
        val chineseScheme: () -> ChineseT9Scheme,
        val isSmartEnglishActive: () -> Boolean,
        val chineseComposing: () -> Boolean,
        val compositionKeyCount: () -> Int,
        val hasPendingPunctuation: () -> Boolean,
        val hasSmartEnglishDigits: () -> Boolean,
        val hasSmartEnglishCandidates: () -> Boolean,
        val hasMultiTapPendingChar: () -> Boolean,
        val hasTopReadingCandidates: () -> Boolean,
        val hasBottomCandidateRow: () -> Boolean,
        val candidateFocus: () -> PhysicalT9KeyHandler.CandidateFocus,
        val idleLongZeroVoiceEnabled: () -> Boolean = { false },
        val heldPastLongPressDelay: (PhysicalT9KeyHandler.KeyInput) -> Boolean
    )

    fun capture(input: PhysicalT9KeyHandler.KeyInput): PhysicalT9KeyFlow.State {
        val mode = source.mode()
        return when (mode) {
            PhysicalT9KeyHandler.Mode.CHINESE -> captureChinese(input)
            PhysicalT9KeyHandler.Mode.ENGLISH -> captureEnglish(input)
            PhysicalT9KeyHandler.Mode.NUMBER -> captureNumber(input)
        }
    }

    private fun captureChinese(input: PhysicalT9KeyHandler.KeyInput) =
        PhysicalT9KeyFlow.State(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            isSmartEnglishActive = false,
            chineseComposing = source.chineseComposing(),
            compositionKeyCount = source.compositionKeyCount(),
            hasPendingPunctuation = source.hasPendingPunctuation(),
            hasSmartEnglishDigits = false,
            hasSmartEnglishCandidates = false,
            hasMultiTapPendingChar = false,
            hasTopReadingCandidates = source.hasTopReadingCandidates(),
            hasBottomCandidateRow = source.hasBottomCandidateRow(),
            candidateFocus = source.candidateFocus(),
            heldPastLongPressDelay = source.heldPastLongPressDelay(input),
            chineseScheme = source.chineseScheme(),
            idleLongZeroVoiceEnabled = source.idleLongZeroVoiceEnabled()
        )

    private fun captureEnglish(input: PhysicalT9KeyHandler.KeyInput): PhysicalT9KeyFlow.State {
        val smartEnglish = source.isSmartEnglishActive()
        val pendingPunctuation = source.hasPendingPunctuation()
        val ownsCandidateSurface = smartEnglish || pendingPunctuation
        return PhysicalT9KeyFlow.State(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = smartEnglish,
            chineseComposing = false,
            compositionKeyCount = 0,
            hasPendingPunctuation = pendingPunctuation,
            hasSmartEnglishDigits = smartEnglish && source.hasSmartEnglishDigits(),
            hasSmartEnglishCandidates = smartEnglish && source.hasSmartEnglishCandidates(),
            hasMultiTapPendingChar = source.hasMultiTapPendingChar(),
            hasTopReadingCandidates = false,
            hasBottomCandidateRow = ownsCandidateSurface && source.hasBottomCandidateRow(),
            candidateFocus = if (ownsCandidateSurface) {
                source.candidateFocus()
            } else {
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM
            },
            heldPastLongPressDelay = source.heldPastLongPressDelay(input),
            idleLongZeroVoiceEnabled = source.idleLongZeroVoiceEnabled()
        )
    }

    private fun captureNumber(input: PhysicalT9KeyHandler.KeyInput) =
        PhysicalT9KeyFlow.State(
            mode = PhysicalT9KeyHandler.Mode.NUMBER,
            isSmartEnglishActive = false,
            chineseComposing = false,
            compositionKeyCount = 0,
            hasPendingPunctuation = false,
            hasSmartEnglishDigits = false,
            hasSmartEnglishCandidates = false,
            hasMultiTapPendingChar = false,
            hasTopReadingCandidates = false,
            hasBottomCandidateRow = false,
            candidateFocus = PhysicalT9KeyHandler.CandidateFocus.BOTTOM,
            heldPastLongPressDelay = source.heldPastLongPressDelay(input)
        )
}

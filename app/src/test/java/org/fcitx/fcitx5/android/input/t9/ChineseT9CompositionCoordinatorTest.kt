/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9CompositionCoordinatorTest {

    @Test
    fun snapshotKeepsForwardedDigitsAuthoritativeOverConvertedPreedit() {
        val coordinator = coordinator()

        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_4)
        val snapshot = coordinator.snapshot("g")

        assertEquals("4", snapshot.rawSequence)
        assertEquals("4", snapshot.currentSegment)
        assertEquals(1, snapshot.keyCount)
    }

    @Test
    fun selectedPinyinPrefixCanBeMatchedAndConsumedThroughOneInterface() {
        val coordinator = coordinator()
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_4)
        coordinator.snapshot("g")

        val request = coordinator.selectPinyin("g")
        val candidate = FcitxEvent.Candidate(label = "", text = "个", comment = "g")

        assertTrue(request != null)
        assertTrue(coordinator.candidateMatchesResolvedPrefix(candidate, "g"))
        assertEquals("", coordinator.consumeResolvedPrefix("g"))
        assertFalse(coordinator.hasState())
    }

    @Test
    fun strokeUsesRawCodeSessionWithoutCreatingPinyinFilters() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.STROKE)

        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_1)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_6)
        val stateBeforeEnginePreedit = coordinator.inputState(hasComposingText = false)
        val snapshot = coordinator.snapshot("一？")
        val presentation = coordinator.presentation(
            snapshot.presentationKey(
                pendingPunctuationText = null,
                inputPreedit = "一？",
                candidateComment = "",
                candidateCursorIndex = 0
            )
        )

        assertEquals("16", snapshot.rawSequence)
        assertEquals(2, snapshot.keyCount)
        assertEquals(
            ChineseT9CompositionLifecycle.InputState.COMPOSING,
            stateBeforeEnginePreedit
        )
        assertEquals(emptyList<String>(), snapshot.filterPrefixes)
        assertEquals(emptyList<String>(), presentation.readingOptions)
        assertEquals("一？", presentation.topReading?.toString())
    }

    @Test
    fun zhuyinUsesCandidateReadingAndSchemeSwitchClearsOldCode() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.STROKE)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_1)

        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_8)
        val snapshot = coordinator.snapshot("38")
        val presentation = coordinator.presentation(
            snapshot.presentationKey(
                pendingPunctuationText = null,
                inputPreedit = "38",
                candidateComment = "ㄏㄠ",
                candidateCursorIndex = 0
            )
        )

        assertEquals("38", snapshot.rawSequence)
        assertEquals(ChineseT9Scheme.ZHUYIN, snapshot.scheme)
        assertEquals("ㄏㄠ", presentation.topReading?.toString())
    }

    @Test
    fun zhuyinWaitsForFocusedCandidateInsteadOfPublishingAnArbitraryReading() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_8)

        val snapshot = coordinator.snapshot("")
        val presentation = coordinator.presentation(
            snapshot.presentationKey(
                pendingPunctuationText = null,
                inputPreedit = "",
                candidateComment = "",
                candidateCursorIndex = -1
            )
        )

        assertFalse(snapshot.hasInvalidReading)
        assertEquals(null, presentation.topReading)
        assertTrue(presentation.readingOptions.isEmpty())
        assertTrue(coordinator.readingCandidates().isEmpty())
    }

    @Test
    fun zhuyinMultiSyllablePreviewUsesTheFocusedRimeReadingWithoutAFilterRow() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        listOf(
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_8
        ).forEach(coordinator::handleForwardedKeyDown)
        val snapshot = coordinator.snapshot("")
        val presentation = coordinator.presentation(
            snapshot.presentationKey(
                pendingPunctuationText = null,
                inputPreedit = "20'38",
                candidateComment = "ㄋㄧ'ㄏㄠ",
                candidateCursorIndex = 0,
                candidateText = "你好"
            )
        )

        assertEquals("ㄋㄧ ㄏㄠ", presentation.topReading?.toString())
        assertTrue(presentation.readingOptions.isEmpty())
        assertTrue(snapshot.filterPrefixes.isEmpty())
        assertEquals("ㄋㄧㄏㄠ", coordinator.literalCommitText("ㄋㄧ ㄏㄠ"))
    }

    @Test
    fun zhuyinCandidateWithoutReadingUsesItsVisibleTextForPreview() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)

        val snapshot = coordinator.snapshot("")
        val presentation = coordinator.presentation(
            snapshot.presentationKey(
                pendingPunctuationText = null,
                inputPreedit = "3",
                candidateComment = "",
                candidateCursorIndex = 0,
                candidateText = "👌"
            )
        )

        assertEquals("👌", presentation.topReading?.toString())
    }

    @Test
    fun invalidZhuyinKeepsRawDigitsAndExposesNonInteractiveNoMatchState() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)

        val snapshot = coordinator.snapshot("")
        val presentation = coordinator.presentation(
            snapshot.presentationKey(
                pendingPunctuationText = null,
                inputPreedit = "",
                candidateComment = "",
                candidateCursorIndex = -1
            )
        )

        assertTrue(snapshot.hasInvalidReading)
        assertEquals("33", presentation.topReading?.toString())
        assertEquals(T9CandidateStatus.NO_MATCH, presentation.candidateStatus)
        assertTrue(presentation.readingOptions.isEmpty())
        assertEquals(null, coordinator.literalCommitText("33"))
    }

    @Test
    fun selectingRawSchemeCandidateClearsItsCompositionCode() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.STROKE)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_1)

        val consumed = coordinator.consumeSelectedCandidateReading(
            FcitxEvent.Candidate(label = "", text = "一", comment = "")
        )

        assertTrue(consumed)
        assertEquals(0, coordinator.keyCount())
        assertFalse(coordinator.hasState())
    }

    @Test
    fun pinyinLiteralCommitOmitsPresentationSeparators() {
        val coordinator = coordinator()

        assertEquals("kale", coordinator.literalCommitText("ka' le"))
        assertEquals(null, coordinator.literalCommitText("' "))
    }

    @Test
    fun literalCommitTextRejectsAmbiguousZhuyinFallback() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_8)

        assertEquals("ㄏㄠ", coordinator.literalCommitText("ㄏㄠ"))
        assertEquals(null, coordinator.literalCommitText("ㄍㄎㄏ ㄞㄟㄠㄡ"))
    }

    @Test
    fun strokeLiteralCommitUsesOneDisplayedSymbolPerCode() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.STROKE)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_1)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_6)

        assertEquals("一？", coordinator.literalCommitText("一？"))
        assertEquals(null, coordinator.literalCommitText("一"))
    }

    @Test
    fun realSubModeTransitionCanResetTwoSchemasWithSameSchemeKind() {
        val coordinator = coordinator()
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_4)

        coordinator.activateScheme(ChineseT9Scheme.PINYIN, forceReset = true)

        assertFalse(coordinator.hasState())
        assertEquals(0, coordinator.keyCount())
    }

    private fun coordinator() = ChineseT9CompositionCoordinator(
        formatText = ::formatted,
        buildRawPreeditDisplay = ::formatted
    )

    private fun formatted(text: String): FormattedText? =
        text.takeIf { it.isNotEmpty() }?.let {
            FormattedText(
                strings = arrayOf(it),
                flags = intArrayOf(TextFormatFlag.NoFlag.flag),
                cursor = -1
            )
        }
}

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
    fun sameCodeCommentCannotOverrideAnExplicitChoice() {
        val coordinator = coordinator()
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_4)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)
        coordinator.selectPinyin("ge")
        val ticket = coordinator.compositionTicket()
        val snapshot = coordinator.snapshot()
        val preview = coordinator.presentation(snapshot.presentationKey(null, "he", "he", 0))
        assertEquals("ge", preview.topReading.toString())
        assertFalse(coordinator.candidateMatchesResolvedPrefix(FcitxEvent.Candidate("", "和", "he"), "ge"))
        coordinator.snapshot()
        assertEquals(ticket, coordinator.compositionTicket())
    }

    @Test
    fun cursorUpdatePreservesLocalCompositionWhileEngineIsRecovering() {
        val coordinator = coordinator()
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_6)

        assertTrue(
            coordinator.shouldPreserveDeferredComposition(
                isActive = true,
                engineWaiting = true
            )
        )
        assertFalse(
            coordinator.shouldPreserveDeferredComposition(
                isActive = true,
                engineWaiting = false
            )
        )
    }

    @Test
    fun snapshotKeepsForwardedDigitsAuthoritativeOverConvertedPreedit() {
        val coordinator = coordinator()

        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_4)
        val snapshot = coordinator.snapshot()

        assertEquals("4", snapshot.rawSequence)
        assertEquals("4", snapshot.currentSegment)
        assertEquals(1, snapshot.keyCount)
    }

    @Test
    fun selectedPinyinPrefixCanBeMatchedAndConsumedThroughOneInterface() {
        val coordinator = coordinator()
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_4)
        coordinator.snapshot()

        val request = coordinator.selectPinyin("g")
        val candidate = FcitxEvent.Candidate(label = "", text = "个", comment = "g")

        assertTrue(request != null)
        assertTrue(coordinator.candidateMatchesResolvedPrefix(candidate, "g"))
        assertEquals("", coordinator.consumeSelectedCandidateReading(candidate))
        assertFalse(coordinator.hasState())
    }

    @Test
    fun strokeUsesRawCodeSessionWithoutCreatingPinyinFilters() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.STROKE)

        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_1)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_6)
        val stateBeforeEnginePreedit = coordinator.inputState(hasComposingText = false)
        val snapshot = coordinator.snapshot()
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
        val snapshot = coordinator.snapshot()
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
    fun zhuyinPublishesReadingOptionsButWaitsForFocusedCandidatePreview() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_8)

        val snapshot = coordinator.snapshot()
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
        assertTrue("ㄏㄠ" in presentation.readingOptions)
        assertEquals(snapshot.explicitReadingOptions, coordinator.readingCandidates())
    }

    @Test
    fun zhuyinMultiSyllablePreviewAndDefaultFilterUseSeparateSources() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        listOf(
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_8
        ).forEach(coordinator::handleForwardedKeyDown)
        val snapshot = coordinator.snapshot()
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
        assertTrue(presentation.readingOptions.isNotEmpty())
        assertTrue(presentation.readingOptions.all {
            T9ZhuyinResolver.digitsForReading(it) == "2038"
        })
        assertTrue(snapshot.filterPrefixes.isEmpty())
        assertEquals("ㄋㄧㄏㄠ", coordinator.literalCommitText("ㄋㄧ ㄏㄠ"))
    }

    @Test
    fun zhuyinDefaultFilterSelectionFiltersCandidateCommentsAndStaysVisible() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_8)

        val available = coordinator.snapshot()
        val availablePresentation = coordinator.presentation(
            available.presentationKey(
                pendingPunctuationText = null,
                inputPreedit = "38",
                candidateComment = "ㄏㄠ",
                candidateCursorIndex = 0,
                candidateText = "好"
            )
        )

        assertTrue("ㄏㄠ" in availablePresentation.readingOptions)
        assertTrue(available.filterPrefixes.isEmpty())
        assertTrue(coordinator.selectZhuyinReading("ㄏㄠ"))

        val selected = coordinator.snapshot()
        assertEquals(listOf("ㄏㄠ"), selected.filterPrefixes)
        assertTrue("ㄏㄠ" in selected.explicitReadingOptions)
        assertTrue(
            coordinator.candidateMatchesResolvedPrefix(
                FcitxEvent.Candidate(label = "", text = "好", comment = "ㄏㄠ"),
                "ㄏㄠ"
            )
        )
        assertFalse(
            coordinator.candidateMatchesResolvedPrefix(
                FcitxEvent.Candidate(label = "", text = "该", comment = "ㄍㄞ"),
                "ㄏㄠ"
            )
        )
    }

    @Test
    fun zhuyinRawCodeMutationReplacesVisibleOptionsAndClearsSelection() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_8)
        assertTrue(coordinator.selectZhuyinReading("ㄏㄠ"))
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_DEL)
        val changed = coordinator.snapshot()
        assertTrue(changed.explicitReadingOptions.isNotEmpty())
        assertTrue(changed.explicitReadingOptions.all {
            T9ZhuyinResolver.digitsForReading(it) == "3"
        })
        assertTrue(changed.filterPrefixes.isEmpty())
    }

    @Test
    fun zhuyinSelectedSyllableSurvivesAppendAndTailDeletion() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_2)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_0)
        assertTrue(coordinator.selectZhuyinReading("ㄋㄩ"))

        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_9)

        val extended = coordinator.snapshot()
        assertEquals("209", extended.rawSequence)
        assertEquals(listOf("ㄋㄩ"), extended.filterPrefixes)
        assertTrue("ㄋㄩ ㄦ" in extended.explicitReadingOptions)
        assertTrue(extended.explicitReadingOptions.all { it.startsWith("ㄋㄩ ") })
        assertTrue(coordinator.candidateMatchesResolvedPrefix(
            FcitxEvent.Candidate("", "女儿", "ㄋㄩ'ㄦ"), "ㄋㄩ"
        ))
        assertFalse(coordinator.candidateMatchesResolvedPrefix(
            FcitxEvent.Candidate("", "天", "ㄊㄧㄢ"), "ㄋㄩ"
        ))

        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_DEL)
        assertEquals("ㄋㄩ", coordinator.snapshot().selectedReading)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_DEL)
        assertTrue(coordinator.snapshot().filterPrefixes.isEmpty())
    }

    @Test
    fun zhuyinPartialCommitPreservesUnconsumedReadingSelection() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_2)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_0)
        assertTrue(coordinator.selectZhuyinReading("ㄋㄧ"))
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_8)
        assertTrue(coordinator.selectZhuyinReading("ㄋㄧ ㄏㄠ"))

        assertEquals("38", coordinator.consumeSelectedCandidateReading(
            FcitxEvent.Candidate("", "你", "ㄋㄧ")
        ))

        assertEquals("38", coordinator.snapshot().rawSequence)
        assertEquals(listOf("ㄏㄠ"), coordinator.snapshot().filterPrefixes)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_9)
        assertTrue(coordinator.snapshot().explicitReadingOptions.all { it.startsWith("ㄏㄠ ") })
    }

    @Test
    fun zhuyinInvalidExtensionNeverSilentlyReinterpretsSelectedInitial() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_2)
        assertTrue(coordinator.selectZhuyinReading("ㄋ"))
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)

        val snapshot = coordinator.snapshot()
        assertTrue(snapshot.hasInvalidReading)
        assertEquals("ㄋ", snapshot.selectedReading)
        assertEquals(T9CandidateStatus.NO_MATCH, coordinator.presentation(snapshot.presentationKey(
            pendingPunctuationText = null,
            inputPreedit = "23",
            candidateComment = "",
            candidateCursorIndex = -1
        )).candidateStatus)

        coordinator.backspaceFromVirtualKey()
        assertFalse(coordinator.snapshot().hasInvalidReading)
        assertEquals("ㄋ", coordinator.snapshot().selectedReading)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_0)
        assertTrue("ㄋㄩ" in coordinator.readingCandidates())
    }

    @Test
    fun zhuyinCandidateWithoutReadingUsesItsVisibleTextForPreview() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_3)

        val snapshot = coordinator.snapshot()
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

        val snapshot = coordinator.snapshot()
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

        val remaining = coordinator.consumeSelectedCandidateReading(
            FcitxEvent.Candidate(label = "", text = "一", comment = "")
        )

        assertEquals("", remaining)
        assertEquals(0, coordinator.keyCount())
        assertFalse(coordinator.hasState())
    }

    @Test
    fun selectingZhuyinCandidateKeepsTheUnreadSyllable() {
        val coordinator = coordinator()
        coordinator.activateScheme(ChineseT9Scheme.ZHUYIN)
        listOf(
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_8
        ).forEach(coordinator::handleForwardedKeyDown)

        val remaining = coordinator.consumeSelectedCandidateReading(
            FcitxEvent.Candidate(label = "", text = "你", comment = "ㄋㄧ")
        )

        assertEquals("38", remaining)
        assertEquals("38", coordinator.snapshot().rawSequence)
        assertTrue(coordinator.readingCandidates().isNotEmpty())
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

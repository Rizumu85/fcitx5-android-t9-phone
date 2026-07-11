/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalT9KeyHandlerTest {

    @Test
    fun firstChineseDigitStartsTraceBeforeOuterForwarding() {
        T9ResponsivenessTrace.configure(enabled = true)
        try {
            val host = FakeHost(
                mode = PhysicalT9KeyHandler.Mode.CHINESE,
                chineseComposing = false,
                compositionKeyCount = 0
            )
            val handler = PhysicalT9KeyHandler(host)

            val result = handler.handleKeyDown(
                keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN)
            )

            assertFalse(result.handled)
            assertEquals(KeyEvent.KEYCODE_2, result.consumedKeyUp)
            assertNotNull(T9ResponsivenessTrace.activeInputId())
        } finally {
            T9ResponsivenessTrace.configure(enabled = false)
        }
    }

    @Test
    fun smartEnglishShortPressAppendsDigitOnKeyUpOnly() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true
        )
        val handler = PhysicalT9KeyHandler(host)

        val down = handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN))
        assertTrue(down.handled)
        assertEquals(emptyList<Int>(), host.appendedSmartEnglishDigits)
        assertEquals(emptyList<String>(), host.committedTexts)

        val up = handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP))
        assertTrue(up.handled)
        assertEquals(listOf(2), host.appendedSmartEnglishDigits)
        assertEquals(emptyList<String>(), host.committedTexts)
    }

    @Test
    fun smartEnglishLongPressCommitsLiteralDigitAndConsumesKeyUp() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true
        )
        val handler = PhysicalT9KeyHandler(host)

        handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN))
        val repeat = handler.handleKeyDown(
            keyInput(
                keyCode = KeyEvent.KEYCODE_2,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                eventTime = 700L
            )
        )
        assertTrue(repeat.handled)
        assertEquals(listOf("2"), host.committedTexts)
        assertEquals(1, host.resetSmartEnglishCount)
        assertEquals(1, host.flushEnglishLearningCount)

        val up = handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP))
        assertTrue(up.handled)
        assertEquals(emptyList<Int>(), host.appendedSmartEnglishDigits)
        assertEquals(listOf("2"), host.committedTexts)
    }

    @Test
    fun smartEnglishPredictionLongPressCommitsShortcutWithoutDigits() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true,
            hasSmartEnglishDigits = false,
            hasSmartEnglishCandidates = true,
            smartEnglishShortcutResult = true
        )
        val handler = PhysicalT9KeyHandler(host)

        handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN))
        val repeat = handler.handleKeyDown(
            keyInput(
                keyCode = KeyEvent.KEYCODE_2,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                eventTime = 700L
            )
        )

        assertTrue(repeat.handled)
        assertEquals(listOf(KeyEvent.KEYCODE_2), host.smartEnglishShortcutKeys)
        assertEquals(emptyList<String>(), host.committedTexts)
        assertEquals(0, host.resetSmartEnglishCount)
    }

    @Test
    fun smartEnglishNavigationConsumesKeyUp() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true,
            hasSmartEnglishDigits = true
        )
        val handler = PhysicalT9KeyHandler(host)

        val result = handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN))

        assertTrue(result.handled)
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, result.consumedKeyUp)
        assertEquals(listOf(-1), host.bottomCandidatePageOffsets)
    }

    @Test
    fun smartEnglishConfirmKeyCommitsCurrentCandidate() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true,
            hasSmartEnglishDigits = true
        )
        val handler = PhysicalT9KeyHandler(host)

        val down = handler.handleKeyDown(
            keyInput(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN)
        )

        assertTrue(down.handled)
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, down.consumedKeyUp)
        assertEquals(1, host.commitSmartEnglishCandidateCount)
    }

    @Test
    fun smartEnglishButtonSelectKeyCommitsCurrentCandidate() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true,
            hasSmartEnglishDigits = true
        )
        val handler = PhysicalT9KeyHandler(host)

        val down = handler.handleKeyDown(
            keyInput(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.ACTION_DOWN)
        )

        assertTrue(down.handled)
        assertEquals(KeyEvent.KEYCODE_BUTTON_SELECT, down.consumedKeyUp)
        assertEquals(1, host.commitSmartEnglishCandidateCount)
    }

    @Test
    fun smartEnglishSpaceConfirmKeyCommitsCurrentCandidate() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true,
            hasSmartEnglishDigits = true
        )
        val handler = PhysicalT9KeyHandler(host)

        val down = handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_SPACE, KeyEvent.ACTION_DOWN))

        assertTrue(down.handled)
        assertEquals(KeyEvent.KEYCODE_SPACE, down.consumedKeyUp)
        assertEquals(1, host.commitSmartEnglishCandidateCount)
    }

    @Test
    fun smartEnglishPredictionCandidateCanBeConfirmedWithoutDigits() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true,
            hasSmartEnglishDigits = false,
            hasSmartEnglishCandidates = true
        )
        val handler = PhysicalT9KeyHandler(host)

        val down = handler.handleKeyDown(
            keyInput(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN)
        )

        assertTrue(down.handled)
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, down.consumedKeyUp)
        assertEquals(1, host.commitSmartEnglishCandidateCount)
    }

    @Test
    fun smartEnglishOneKeyCyclesCaseWithoutCommittingPrediction() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true,
            hasSmartEnglishDigits = false,
            hasSmartEnglishCandidates = true
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_1, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_1, KeyEvent.ACTION_UP)).handled)

        assertEquals(0, host.resetSmartEnglishCount)
        assertEquals(1, host.cycleEnglishCaseCount)
        assertEquals(0, host.showEnglishPunctuationCount)
        assertEquals(emptyList<Boolean>(), host.commitSmartEnglishCandidateAppendSpace)
        assertEquals(emptyList<String>(), host.committedTexts)
    }

    @Test
    fun smartEnglishStarCommitsTypedCandidateWithoutSpaceThenShowsPunctuation() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true,
            hasSmartEnglishDigits = true,
            hasSmartEnglishCandidates = true
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_UP)).handled)

        assertEquals(0, host.resetSmartEnglishCount)
        assertEquals(1, host.showEnglishPunctuationCount)
        assertEquals(listOf(false), host.commitSmartEnglishCandidateAppendSpace)
        assertEquals(listOf(false), host.commitSmartEnglishCandidateContinuePrediction)
    }

    @Test
    fun smartEnglishPoundShortPressCommitsPredictionWithoutSpaceThenReturns() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true,
            hasSmartEnglishDigits = false,
            hasSmartEnglishCandidates = true
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP)).handled)

        assertEquals(0, host.resetSmartEnglishCount)
        assertEquals(1, host.handleReturnCount)
        assertEquals(listOf(false), host.commitSmartEnglishCandidateAppendSpace)
        assertEquals(listOf(false), host.commitSmartEnglishCandidateContinuePrediction)
        assertEquals(emptyList<String>(), host.committedTexts)
    }

    @Test
    fun smartEnglishPoundShortPressCommitsTypedCandidateWithoutSpaceThenReturns() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true,
            hasSmartEnglishDigits = true,
            hasSmartEnglishCandidates = true
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP)).handled)

        assertEquals(0, host.resetSmartEnglishCount)
        assertEquals(1, host.handleReturnCount)
        assertEquals(listOf(false), host.commitSmartEnglishCandidateAppendSpace)
        assertEquals(listOf(false), host.commitSmartEnglishCandidateContinuePrediction)
    }

    @Test
    fun chineseZeroShortPressCommitsHighlightedHanziWithoutSpace() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            chineseComposing = true,
            compositionKeyCount = 2,
            candidateFocus = PhysicalT9KeyHandler.CandidateFocus.BOTTOM,
            commitHighlightedBottomCandidateResult = true
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_0, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_0, KeyEvent.ACTION_UP)).handled)

        assertEquals(1, host.commitHighlightedBottomCandidateCount)
        assertEquals(emptyList<String>(), host.committedTexts)
    }

    @Test
    fun chineseIdleZeroShortPressOnlyCommitsSpace() {
        val host = FakeHost(mode = PhysicalT9KeyHandler.Mode.CHINESE)
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_0, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_0, KeyEvent.ACTION_UP)).handled)

        assertEquals(listOf(" "), host.committedTexts)
        assertEquals(0, host.commitHighlightedBottomCandidateCount)
        assertEquals(emptyList<Int>(), host.hanziShortcutKeys)
    }

    @Test
    fun chineseCompositionDigitShortPressForwardsThroughHost() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            chineseComposing = true,
            compositionKeyCount = 2
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP)).handled)

        assertEquals(listOf(KeyEvent.KEYCODE_2), host.forwardedChineseT9Keys)
    }

    @Test
    fun chineseIdlePredictiveDigitShortPressFallsThroughToOuterInputFlow() {
        val host = FakeHost(mode = PhysicalT9KeyHandler.Mode.CHINESE)
        val handler = PhysicalT9KeyHandler(host)

        val down = handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN))

        assertFalse(down.handled)
        assertEquals(KeyEvent.KEYCODE_2, down.consumedKeyUp)
        assertFalse(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP)).handled)
        assertEquals(emptyList<String>(), host.committedTexts)
    }

    @Test
    fun chineseTransientComposingWithoutCompositionKeysFallsThroughButConsumesKeyUp() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            chineseComposing = true,
            compositionKeyCount = 0
        )
        val handler = PhysicalT9KeyHandler(host)

        val down = handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_4, KeyEvent.ACTION_DOWN))

        assertFalse(down.handled)
        assertEquals(KeyEvent.KEYCODE_4, down.consumedKeyUp)
    }

    @Test
    fun chineseCompositionPoundShortPressCommitsCodePreviewOnKeyUp() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            chineseComposing = true,
            compositionKeyCount = 2
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN)).handled)
        assertEquals(emptyList<Int>(), host.forwardedChineseT9Keys)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP)).handled)
        assertEquals(1, host.commitChineseCodePreviewCount)
        assertEquals(emptyList<Int>(), host.forwardedChineseT9Keys)
        assertEquals(0, host.handleReturnCount)
    }

    @Test
    fun chineseCompositionKeysPoundCommitsPreviewBeforeEditorComposingArrives() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            chineseComposing = false,
            compositionKeyCount = 2
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN)).handled)
        assertEquals(emptyList<Int>(), host.forwardedChineseT9Keys)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP)).handled)
        assertEquals(1, host.commitChineseCodePreviewCount)
        assertEquals(emptyList<Int>(), host.forwardedChineseT9Keys)
        assertEquals(0, host.handleReturnCount)
    }

    @Test
    fun chineseCompositionPoundLongPressDiscardsCompositionAndSwitchesMode() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            chineseComposing = true,
            compositionKeyCount = 2
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN)).handled)
        assertEquals(emptyList<Int>(), host.forwardedChineseT9Keys)
        val repeat = handler.handleKeyDown(
            keyInput(
                keyCode = KeyEvent.KEYCODE_POUND,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                eventTime = 700L
            )
        )
        val up = handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP))

        assertTrue(repeat.handled)
        assertEquals(KeyEvent.KEYCODE_POUND, repeat.consumedKeyUp)
        assertEquals(1, host.discardChineseCompositionForModeSwitchCount)
        assertEquals(1, host.switchToNextModeCount)
        assertEquals(emptyList<Int>(), host.forwardedChineseT9Keys)
        assertTrue(up.handled)
    }

    @Test
    fun chineseCompositionKeysPoundLongPressDiscardsBeforeEditorComposingArrives() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            chineseComposing = false,
            compositionKeyCount = 2
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN)).handled)
        assertEquals(emptyList<Int>(), host.forwardedChineseT9Keys)
        val repeat = handler.handleKeyDown(
            keyInput(
                keyCode = KeyEvent.KEYCODE_POUND,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                eventTime = 700L
            )
        )
        val up = handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP))

        assertTrue(repeat.handled)
        assertEquals(KeyEvent.KEYCODE_POUND, repeat.consumedKeyUp)
        assertEquals(1, host.discardChineseCompositionForModeSwitchCount)
        assertEquals(1, host.switchToNextModeCount)
        assertEquals(emptyList<Int>(), host.forwardedChineseT9Keys)
        assertTrue(up.handled)
    }

    @Test
    fun chineseIdlePoundShortPressHandlesReturn() {
        val host = FakeHost(mode = PhysicalT9KeyHandler.Mode.CHINESE)
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP)).handled)

        assertEquals(1, host.handleReturnCount)
    }

    @Test
    fun chineseIdlePoundReturnsEvenWhenSeveralSchemesAreConfigured() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            cycleChineseSchemeResult = true
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP)).handled)

        assertEquals(0, host.cycleChineseSchemeCount)
        assertEquals(1, host.handleReturnCount)
    }

    @Test
    fun chineseIdleLongStarCyclesSchemeOrCommitsLiteralFallback() {
        val cycleHost = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            cycleChineseSchemeResult = true
        )
        val cycleHandler = PhysicalT9KeyHandler(cycleHost)
        cycleHandler.handleKeyDown(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_DOWN))
        cycleHandler.handleKeyDown(
            keyInput(
                keyCode = KeyEvent.KEYCODE_STAR,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                eventTime = 700L
            )
        )
        cycleHandler.handleKeyUp(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_UP))

        val fallbackHost = FakeHost(mode = PhysicalT9KeyHandler.Mode.CHINESE)
        val fallbackHandler = PhysicalT9KeyHandler(fallbackHost)
        fallbackHandler.handleKeyDown(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_DOWN))
        fallbackHandler.handleKeyDown(
            keyInput(
                keyCode = KeyEvent.KEYCODE_STAR,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                eventTime = 700L
            )
        )
        fallbackHandler.handleKeyUp(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_UP))

        assertEquals(1, cycleHost.cycleChineseSchemeCount)
        assertEquals(0, cycleHost.commitLiteralStarCount)
        assertEquals(1, fallbackHost.cycleChineseSchemeCount)
        assertEquals(1, fallbackHost.commitLiteralStarCount)
    }

    @Test
    fun pinyinIdleOneShortPressCommitsSeparator() {
        val host = FakeHost(mode = PhysicalT9KeyHandler.Mode.CHINESE)
        val handler = PhysicalT9KeyHandler(host)

        handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_1, KeyEvent.ACTION_DOWN))
        handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_1, KeyEvent.ACTION_UP))

        assertEquals(listOf("'"), host.committedTexts)
    }

    @Test
    fun chineseStarShortPressShowsChinesePunctuation() {
        val host = FakeHost(mode = PhysicalT9KeyHandler.Mode.CHINESE)
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_UP)).handled)

        assertEquals(1, host.showChinesePunctuationCount)
        assertEquals(0, host.commitLiteralStarCount)
        assertEquals(0, host.togglePendingPunctuationSetCount)
    }

    @Test
    fun chinesePendingPunctuationZeroShortPressCommitsWithoutSpace() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            hasPendingPunctuation = true
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_0, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_0, KeyEvent.ACTION_UP)).handled)

        assertEquals(1, host.commitPendingPunctuationCount)
        assertEquals(emptyList<String>(), host.committedTexts)
    }

    @Test
    fun chinesePendingPunctuationOneShortPressCommitsThenTypesOne() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            hasPendingPunctuation = true
        )
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_1, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_1, KeyEvent.ACTION_UP)).handled)

        assertEquals(1, host.commitPendingPunctuationCount)
        assertEquals(listOf("1"), host.committedTexts)
    }

    @Test
    fun chinesePendingPunctuationDigitLongPressFallsBackToLiteralWhenShortcutIsMissing() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            hasPendingPunctuation = true
        )
        val handler = PhysicalT9KeyHandler(host)

        handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_9, KeyEvent.ACTION_DOWN))
        val repeat = handler.handleKeyDown(
            keyInput(
                keyCode = KeyEvent.KEYCODE_9,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                eventTime = 700L
            )
        )
        val up = handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_9, KeyEvent.ACTION_UP))

        assertTrue(repeat.handled)
        assertTrue(up.handled)
        assertEquals(listOf(KeyEvent.KEYCODE_9), host.pendingPunctuationShortcutKeys)
        assertEquals(1, host.cancelPendingPunctuationCount)
        assertEquals(listOf("9"), host.committedTexts)
    }

    @Test
    fun chinesePendingPunctuationBackspaceCancelsAndConsumesKeyUp() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            hasPendingPunctuation = true
        )
        val handler = PhysicalT9KeyHandler(host)

        val down = handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN))

        assertTrue(down.handled)
        assertEquals(KeyEvent.KEYCODE_DEL, down.consumedKeyUp)
        assertEquals(1, host.cancelPendingPunctuationCount)
    }

    @Test
    fun chinesePendingPunctuationNavigationConsumesEvenWhenPageCannotMove() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            hasPendingPunctuation = true
        )
        val handler = PhysicalT9KeyHandler(host)

        val result = handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN))

        assertTrue(result.handled)
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, result.consumedKeyUp)
        assertEquals(listOf(-1), host.bottomCandidatePageOffsets)
    }

    @Test
    fun chineseCandidateNavigationConsumesWhenCandidateRowExists() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.CHINESE,
            hasBottomCandidateRow = true,
            candidateFocus = PhysicalT9KeyHandler.CandidateFocus.BOTTOM
        )
        val handler = PhysicalT9KeyHandler(host)

        val result = handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN))

        assertTrue(result.handled)
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, result.consumedKeyUp)
        assertEquals(listOf(1), host.bottomCandidateMoves)
    }

    @Test
    fun chineseCandidateNavigationFallsThroughWithoutCandidateRows() {
        val host = FakeHost(mode = PhysicalT9KeyHandler.Mode.CHINESE)
        val handler = PhysicalT9KeyHandler(host)

        val result = handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN))

        assertFalse(result.handled)
    }

    @Test
    fun numberDigitShortPressCommitsDigitThroughCommandFlow() {
        val host = FakeHost(mode = PhysicalT9KeyHandler.Mode.NUMBER)
        val handler = PhysicalT9KeyHandler(host)

        assertTrue(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN)).handled)
        assertTrue(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP)).handled)

        assertEquals(listOf("2"), host.committedTexts)
        assertEquals(emptyList<Int>(), host.numberOperatorKeys)
    }

    @Test
    fun numberDigitLongPressUsesOperatorAndSuppressesDigit() {
        val host = FakeHost(mode = PhysicalT9KeyHandler.Mode.NUMBER)
        val handler = PhysicalT9KeyHandler(host)

        handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN))
        val repeat = handler.handleKeyDown(
            keyInput(
                keyCode = KeyEvent.KEYCODE_2,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                eventTime = 700L
            )
        )
        val up = handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP))

        assertTrue(repeat.handled)
        assertTrue(up.handled)
        assertEquals(listOf(KeyEvent.KEYCODE_2), host.numberOperatorKeys)
        assertEquals(listOf(2), host.numberOperatorFallbackDigits)
        assertEquals(emptyList<String>(), host.committedTexts)
    }

    @Test
    fun numberStarShortCommitsLiteralAndLongShowsOperatorHints() {
        val shortHost = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.NUMBER,
            commitSmartEnglishCandidateResult = false
        )
        val shortHandler = PhysicalT9KeyHandler(shortHost)

        shortHandler.handleKeyDown(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_DOWN))
        shortHandler.handleKeyUp(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_UP))

        val longHost = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.NUMBER,
            commitSmartEnglishCandidateResult = false
        )
        val longHandler = PhysicalT9KeyHandler(longHost)
        longHandler.handleKeyDown(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_DOWN))
        longHandler.handleKeyDown(
            keyInput(
                keyCode = KeyEvent.KEYCODE_STAR,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                eventTime = 700L
            )
        )
        longHandler.handleKeyUp(keyInput(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_UP))

        assertEquals(1, shortHost.commitLiteralStarCount)
        assertEquals(0, shortHost.showNumberOperatorHintPanelCount)
        assertEquals(0, longHost.commitLiteralStarCount)
        assertEquals(1, longHost.showNumberOperatorHintPanelCount)
    }

    @Test
    fun numberPoundShortReturnsAndLongPressSwitchesMode() {
        val shortHost = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.NUMBER,
            commitSmartEnglishCandidateResult = false
        )
        val shortHandler = PhysicalT9KeyHandler(shortHost)

        shortHandler.handleKeyDown(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN))
        shortHandler.handleKeyUp(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP))

        val longHost = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.NUMBER,
            commitSmartEnglishCandidateResult = false
        )
        val longHandler = PhysicalT9KeyHandler(longHost)
        longHandler.handleKeyDown(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN))
        longHandler.handleKeyDown(
            keyInput(
                keyCode = KeyEvent.KEYCODE_POUND,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                eventTime = 700L
            )
        )
        longHandler.handleKeyUp(keyInput(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP))

        assertEquals(1, shortHost.handleReturnCount)
        assertEquals(0, shortHost.switchToNextModeCount)
        assertEquals(0, longHost.handleReturnCount)
        assertEquals(1, longHost.switchToNextModeCount)
    }

    @Test
    fun ignoresKeysOutsideInputMode() {
        val host = FakeHost(
            isInInputMode = false,
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true
        )
        val handler = PhysicalT9KeyHandler(host)

        assertFalse(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN)).handled)
        assertFalse(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP)).handled)
    }

    private fun keyInput(
        keyCode: Int,
        action: Int,
        repeatCount: Int = 0,
        downTime: Long = 0L,
        eventTime: Long = downTime
    ): PhysicalT9KeyHandler.KeyInput = PhysicalT9KeyHandler.KeyInput(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        downTime = downTime,
        eventTime = eventTime
    )

    private class FakeHost(
        override val isInInputMode: Boolean = true,
        override var mode: PhysicalT9KeyHandler.Mode = PhysicalT9KeyHandler.Mode.CHINESE,
        override var isSmartEnglishActive: Boolean = false,
        override var chineseComposing: Boolean = false,
        override var compositionKeyCount: Int = 0,
        override var hasPendingPunctuation: Boolean = false,
        override var hasSmartEnglishDigits: Boolean = false,
        override var hasSmartEnglishCandidates: Boolean = hasSmartEnglishDigits,
        override var hasMultiTapPendingChar: Boolean = false,
        override var hasTopReadingCandidates: Boolean = false,
        override var hasBottomCandidateRow: Boolean = false,
        override var candidateFocus: PhysicalT9KeyHandler.CandidateFocus =
            PhysicalT9KeyHandler.CandidateFocus.BOTTOM,
        override var idleLongZeroVoiceEnabled: Boolean = false,
        private val commitHighlightedBottomCandidateResult: Boolean = false,
        private val smartEnglishShortcutResult: Boolean = false,
        private val commitSmartEnglishCandidateResult: Boolean = true,
        private val cycleChineseSchemeResult: Boolean = false
    ) : PhysicalT9KeyHandler.Host {
        val committedTexts = mutableListOf<String>()
        val appendedSmartEnglishDigits = mutableListOf<Int>()
        val smartEnglishShortcutKeys = mutableListOf<Int>()
        val bottomCandidatePageOffsets = mutableListOf<Int>()
        var resetSmartEnglishCount = 0
        var flushEnglishLearningCount = 0
        var commitSmartEnglishCandidateCount = 0
        val commitSmartEnglishCandidateAppendSpace = mutableListOf<Boolean>()
        val commitSmartEnglishCandidateContinuePrediction = mutableListOf<Boolean>()
        var commitHighlightedBottomCandidateCount = 0
        var showChinesePunctuationCount = 0
        var showEnglishPunctuationCount = 0
        var cycleEnglishCaseCount = 0
        var handleReturnCount = 0
        var commitChineseCodePreviewCount = 0
        var cycleChineseSchemeCount = 0
        val numberOperatorKeys = mutableListOf<Int>()
        val numberOperatorFallbackDigits = mutableListOf<Int>()
        var showNumberOperatorHintPanelCount = 0
        var commitLiteralStarCount = 0
        var switchToNextModeCount = 0
        var switchToVoiceInputCount = 0
        var commitPendingPunctuationCount = 0
        var cancelPendingPunctuationCount = 0
        val pendingPunctuationShortcutKeys = mutableListOf<Int>()
        val hanziShortcutKeys = mutableListOf<Int>()
        val forwardedChineseT9Keys = mutableListOf<Int>()
        var forwardChineseT9SeparatorCount = 0
        var togglePendingPunctuationSetCount = 0
        var discardChineseCompositionForModeSwitchCount = 0
        val bottomCandidateMoves = mutableListOf<Int>()
        override fun keyHeldPastLongPressDelay(input: PhysicalT9KeyHandler.KeyInput): Boolean =
            input.repeatCount > 0 && input.eventTime - input.downTime >= 500L

        override fun commitPendingPunctuationShortcut(keyCode: Int): Boolean {
            pendingPunctuationShortcutKeys += keyCode
            return false
        }
        override fun commitHanziShortcut(keyCode: Int): Boolean {
            hanziShortcutKeys += keyCode
            return false
        }
        override fun commitSmartEnglishShortcut(keyCode: Int): Boolean {
            smartEnglishShortcutKeys += keyCode
            return smartEnglishShortcutResult
        }
        override fun commitPendingPunctuation(): Boolean {
            commitPendingPunctuationCount += 1
            return false
        }
        override fun cancelPendingPunctuation(): Boolean {
            cancelPendingPunctuationCount += 1
            return false
        }
        override fun showChinesePunctuationCandidates() {
            showChinesePunctuationCount += 1
        }
        override fun showEnglishPunctuationCandidates() {
            showEnglishPunctuationCount += 1
        }
        override fun commitChineseCandidateAndShowPunctuation() {
            commitHighlightedBottomCandidateCount += 1
            showChinesePunctuationCount += 1
        }
        override fun togglePendingPunctuationSet(): Boolean {
            togglePendingPunctuationSetCount += 1
            return false
        }
        override fun switchToNextMode() {
            switchToNextModeCount += 1
        }
        override fun switchToVoiceInput() {
            switchToVoiceInputCount += 1
        }

        override fun commitText(text: String) {
            committedTexts += text
        }

        override fun commitNumberOperatorForKey(keyCode: Int, fallbackDigit: Int): Boolean {
            numberOperatorKeys += keyCode
            numberOperatorFallbackDigits += fallbackDigit
            return true
        }

        override fun showNumberOperatorHintPanel() {
            showNumberOperatorHintPanelCount += 1
        }

        override fun commitLiteralStar() {
            commitLiteralStarCount += 1
        }
        override fun cycleEnglishCase() {
            cycleEnglishCaseCount += 1
        }
        override fun handleMultiTapKey(keyCode: Int): Boolean = false
        override fun commitMultiTapChar(): Boolean = false
        override fun cancelMultiTapChar() = Unit

        override fun appendSmartEnglishDigit(digit: Int) {
            appendedSmartEnglishDigits += digit
            hasSmartEnglishDigits = true
            hasSmartEnglishCandidates = true
        }

        override fun resetSmartEnglishT9() {
            resetSmartEnglishCount += 1
            hasSmartEnglishDigits = false
            hasSmartEnglishCandidates = false
        }

        override fun commitSmartEnglishCandidate(
            appendSpace: Boolean,
            continuePrediction: Boolean
        ): Boolean {
            commitSmartEnglishCandidateCount += 1
            commitSmartEnglishCandidateAppendSpace += appendSpace
            commitSmartEnglishCandidateContinuePrediction += continuePrediction
            hasSmartEnglishDigits = false
            hasSmartEnglishCandidates = false
            return commitSmartEnglishCandidateResult
        }

        override fun moveSmartEnglishCandidate(delta: Int): Boolean = false
        override fun smartEnglishBackspace(): Boolean = false

        override fun flushEnglishLearningWord() {
            flushEnglishLearningCount += 1
        }

        override fun handleReturnKey() {
            handleReturnCount += 1
        }
        override fun commitChineseCodePreview(): Boolean {
            commitChineseCodePreviewCount += 1
            return true
        }
        override fun cycleChineseScheme(): Boolean {
            cycleChineseSchemeCount += 1
            return cycleChineseSchemeResult
        }
        override fun forwardChineseT9KeyShortPress(
            keyCode: Int,
            input: PhysicalT9KeyHandler.KeyInput
        ): Boolean {
            forwardedChineseT9Keys += keyCode
            return false
        }

        override fun forwardChineseT9SeparatorShortPress(): Boolean {
            forwardChineseT9SeparatorCount += 1
            return false
        }

        override fun discardChineseCompositionForModeSwitch() {
            discardChineseCompositionForModeSwitchCount += 1
        }

        override fun moveCandidateFocus(focus: PhysicalT9KeyHandler.CandidateFocus) {
            candidateFocus = focus
        }

        override fun moveHighlightedReading(delta: Int): Boolean = false
        override fun moveHighlightedBottomCandidate(delta: Int): Boolean {
            bottomCandidateMoves += delta
            return false
        }

        override fun offsetBottomCandidatePage(delta: Int): Boolean {
            bottomCandidatePageOffsets += delta
            return false
        }

        override fun commitHighlightedReading(): Boolean = false

        override fun commitHighlightedBottomCandidate(): Boolean {
            commitHighlightedBottomCandidateCount += 1
            return commitHighlightedBottomCandidateResult
        }
    }
}

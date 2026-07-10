/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhysicalT9StateCaptureTest {

    @Test
    fun numberModeDoesNotReadChineseOrEnglishState() {
        var crossModeReads = 0
        val capture = PhysicalT9StateCapture(
            source(
                mode = PhysicalT9KeyHandler.Mode.NUMBER,
                crossModeRead = { crossModeReads += 1 }
            )
        )

        val state = capture.capture(keyInput())

        assertEquals(PhysicalT9KeyHandler.Mode.NUMBER, state.mode)
        assertEquals(0, crossModeReads)
    }

    @Test
    fun simpleEnglishDoesNotReadChineseOrSmartEnglishCandidateState() {
        var chineseReads = 0
        var smartReads = 0
        val source = source(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            crossModeRead = { chineseReads += 1 }
        ).copy(
            isSmartEnglishActive = { false },
            hasSmartEnglishDigits = { smartReads += 1; true },
            hasSmartEnglishCandidates = { smartReads += 1; true }
        )

        val state = PhysicalT9StateCapture(source).capture(keyInput())

        assertFalse(state.isSmartEnglishActive)
        assertEquals(0, chineseReads)
        assertEquals(0, smartReads)
    }

    private fun source(
        mode: PhysicalT9KeyHandler.Mode,
        crossModeRead: () -> Unit
    ) = PhysicalT9StateCapture.Source(
        mode = { mode },
        chineseScheme = { crossModeRead(); ChineseT9Scheme.PINYIN },
        isSmartEnglishActive = { crossModeRead(); false },
        chineseComposing = { crossModeRead(); false },
        compositionKeyCount = { crossModeRead(); 0 },
        hasPendingPunctuation = { false },
        hasSmartEnglishDigits = { crossModeRead(); false },
        hasSmartEnglishCandidates = { crossModeRead(); false },
        hasMultiTapPendingChar = { false },
        hasTopReadingCandidates = { crossModeRead(); false },
        hasBottomCandidateRow = { crossModeRead(); false },
        candidateFocus = { crossModeRead(); PhysicalT9KeyHandler.CandidateFocus.BOTTOM },
        heldPastLongPressDelay = { false }
    )

    private fun keyInput() = PhysicalT9KeyHandler.KeyInput(
        keyCode = KeyEvent.KEYCODE_2,
        action = KeyEvent.ACTION_DOWN,
        repeatCount = 0,
        downTime = 0L,
        eventTime = 0L
    )
}

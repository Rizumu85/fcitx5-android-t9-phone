/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalT9SelectionModeTest {
    @Test
    fun chineseFocusMovesFromBottomToPinyinRowWhenTopExists() {
        val decision = PhysicalT9SelectionMode.handle(
            input = input(KeyEvent.KEYCODE_DPAD_UP),
            state = state(
                hasTopReadingCandidates = true,
                hasBottomCandidateRow = true,
                candidateFocus = PhysicalT9KeyHandler.CandidateFocus.BOTTOM
            ),
            surface = PhysicalT9SelectionMode.Surface.CHINESE_CANDIDATES
        )

        assertEquals(
            listOf(
                PhysicalT9KeyFlow.Command.MoveCandidateFocus(
                    PhysicalT9KeyHandler.CandidateFocus.TOP
                )
            ),
            decision?.commands
        )
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, decision?.consumedKeyUp)
    }

    @Test
    fun chineseFocusKeyFallsThroughWhenNoCandidateRowsExist() {
        assertNull(
            PhysicalT9SelectionMode.handle(
                input = input(KeyEvent.KEYCODE_DPAD_UP),
                state = state(),
                surface = PhysicalT9SelectionMode.Surface.CHINESE_CANDIDATES
            )
        )
    }

    @Test
    fun smartEnglishSpaceCommitsVisibleBottomCandidate() {
        val decision = PhysicalT9SelectionMode.handle(
            input = input(KeyEvent.KEYCODE_SPACE),
            state = state(hasSmartEnglishCandidates = true),
            surface = PhysicalT9SelectionMode.Surface.SMART_ENGLISH
        )

        assertEquals(
            listOf(
                PhysicalT9KeyFlow.Command.CommitBottomCandidate(
                    PhysicalT9KeyFlow.BottomCandidateFallback.SMART_ENGLISH
                )
            ),
            decision?.commands
        )
        assertEquals(KeyEvent.KEYCODE_SPACE, decision?.consumedKeyUp)
    }

    @Test
    fun smartEnglishHorizontalMoveKeepsLegacyFallbackWhenPipelineCannotMove() {
        val decision = PhysicalT9SelectionMode.handle(
            input = input(KeyEvent.KEYCODE_DPAD_LEFT),
            state = state(hasSmartEnglishCandidates = true),
            surface = PhysicalT9SelectionMode.Surface.SMART_ENGLISH
        )

        assertEquals(
            listOf(
                PhysicalT9KeyFlow.Command.MoveBottomCandidate(
                    delta = -1,
                    fallbackSmartEnglishDelta = -1
                )
            ),
            decision?.commands
        )
    }

    @Test
    fun pendingPunctuationOkCommitsPunctuationFallback() {
        val decision = PhysicalT9SelectionMode.handle(
            input = input(KeyEvent.KEYCODE_DPAD_CENTER),
            state = state(hasPendingPunctuation = true),
            surface = PhysicalT9SelectionMode.Surface.PENDING_PUNCTUATION
        )

        assertEquals(
            listOf(
                PhysicalT9KeyFlow.Command.CommitBottomCandidate(
                    PhysicalT9KeyFlow.BottomCandidateFallback.PENDING_PUNCTUATION
                )
            ),
            decision?.commands
        )
    }

    private fun input(keyCode: Int): PhysicalT9KeyHandler.KeyInput =
        PhysicalT9KeyHandler.KeyInput(
            keyCode = keyCode,
            action = KeyEvent.ACTION_DOWN,
            repeatCount = 0,
            downTime = 0,
            eventTime = 0
        )

    private fun state(
        mode: PhysicalT9KeyHandler.Mode = PhysicalT9KeyHandler.Mode.CHINESE,
        isSmartEnglishActive: Boolean = false,
        chineseComposing: Boolean = false,
        compositionKeyCount: Int = 0,
        hasPendingPunctuation: Boolean = false,
        hasSmartEnglishDigits: Boolean = false,
        hasSmartEnglishCandidates: Boolean = false,
        hasMultiTapPendingChar: Boolean = false,
        hasTopReadingCandidates: Boolean = false,
        hasBottomCandidateRow: Boolean = false,
        candidateFocus: PhysicalT9KeyHandler.CandidateFocus =
            PhysicalT9KeyHandler.CandidateFocus.BOTTOM,
        heldPastLongPressDelay: Boolean = false
    ): PhysicalT9KeyFlow.State =
        PhysicalT9KeyFlow.State(
            mode = mode,
            isSmartEnglishActive = isSmartEnglishActive,
            chineseComposing = chineseComposing,
            compositionKeyCount = compositionKeyCount,
            hasPendingPunctuation = hasPendingPunctuation,
            hasSmartEnglishDigits = hasSmartEnglishDigits,
            hasSmartEnglishCandidates = hasSmartEnglishCandidates,
            hasMultiTapPendingChar = hasMultiTapPendingChar,
            hasTopReadingCandidates = hasTopReadingCandidates,
            hasBottomCandidateRow = hasBottomCandidateRow,
            candidateFocus = candidateFocus,
            heldPastLongPressDelay = heldPastLongPressDelay
        )
}

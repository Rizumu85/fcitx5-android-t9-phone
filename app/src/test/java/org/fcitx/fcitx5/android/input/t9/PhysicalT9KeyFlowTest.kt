/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalT9KeyFlowTest {

    @Test
    fun smartEnglishOneCommitsCandidateWithoutPredictionThenShowsPunctuation() {
        val flow = PhysicalT9KeyFlow()

        assertEquals(emptyList<PhysicalT9KeyFlow.Command>(), flow.handle(
            input(KeyEvent.KEYCODE_1, KeyEvent.ACTION_DOWN),
            state(hasSmartEnglishCandidates = true)
        )?.commands)
        val up = flow.handle(
            input(KeyEvent.KEYCODE_1, KeyEvent.ACTION_UP),
            state(hasSmartEnglishCandidates = true)
        )

        assertEquals(
            listOf(
                PhysicalT9KeyFlow.Command.CommitSmartEnglishCandidate(
                    appendSpace = false,
                    continuePrediction = false
                ),
                PhysicalT9KeyFlow.Command.ShowSmartEnglishPunctuationCandidates
            ),
            up?.commands
        )
    }

    @Test
    fun smartEnglishOneWithoutCandidateShowsPunctuation() {
        val flow = PhysicalT9KeyFlow()
        flow.handle(input(KeyEvent.KEYCODE_1, KeyEvent.ACTION_DOWN), state())

        val up = flow.handle(input(KeyEvent.KEYCODE_1, KeyEvent.ACTION_UP), state())

        assertEquals(
            listOf(PhysicalT9KeyFlow.Command.ShowSmartEnglishPunctuationCandidates),
            up?.commands
        )
    }

    @Test
    fun smartEnglishPoundCommitsCandidateWithoutPredictionThenReturns() {
        val flow = PhysicalT9KeyFlow()
        flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN), state(hasSmartEnglishCandidates = true))

        val up = flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP), state(hasSmartEnglishCandidates = true))

        assertEquals(
            listOf(
                PhysicalT9KeyFlow.Command.CommitSmartEnglishCandidate(
                    appendSpace = false,
                    continuePrediction = false
                ),
                PhysicalT9KeyFlow.Command.HandleReturnKey
            ),
            up?.commands
        )
    }

    @Test
    fun smartEnglishPoundWithoutCandidateReturns() {
        val flow = PhysicalT9KeyFlow()
        flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN), state())

        val up = flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP), state())

        assertEquals(listOf(PhysicalT9KeyFlow.Command.HandleReturnKey), up?.commands)
    }

    @Test
    fun smartEnglishPendingPunctuationKeepsExistingOneAndPoundBehavior() {
        val flow = PhysicalT9KeyFlow()

        flow.handle(input(KeyEvent.KEYCODE_1, KeyEvent.ACTION_DOWN), state(hasPendingPunctuation = true))
        val oneUp = flow.handle(input(KeyEvent.KEYCODE_1, KeyEvent.ACTION_UP), state(hasPendingPunctuation = true))
        flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN), state(hasPendingPunctuation = true))
        val poundUp = flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP), state(hasPendingPunctuation = true))

        assertEquals(emptyList<PhysicalT9KeyFlow.Command>(), oneUp?.commands)
        assertEquals(listOf(PhysicalT9KeyFlow.Command.CommitPendingPunctuation), poundUp?.commands)
    }

    @Test
    fun smartEnglishLongPressPoundCommitsPendingPunctuationThenSwitchesMode() {
        val flow = PhysicalT9KeyFlow()
        flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN), state(hasPendingPunctuation = true))

        val repeat = flow.handle(
            input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN, repeatCount = 1),
            state(hasPendingPunctuation = true, heldPastLongPressDelay = true)
        )
        val up = flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP), state(hasPendingPunctuation = true))

        assertEquals(
            listOf(
                PhysicalT9KeyFlow.Command.CommitPendingPunctuation,
                PhysicalT9KeyFlow.Command.SwitchToNextMode
            ),
            repeat?.commands
        )
        assertEquals(emptyList<PhysicalT9KeyFlow.Command>(), up?.commands)
    }

    @Test
    fun nonSmartEnglishOneAndPoundAreNotHandledByFlow() {
        val flow = PhysicalT9KeyFlow()

        assertNull(flow.handle(input(KeyEvent.KEYCODE_1, KeyEvent.ACTION_DOWN), state(isSmartEnglishActive = false)))
        assertNull(flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN), state(mode = PhysicalT9KeyHandler.Mode.CHINESE)))
    }

    private fun input(
        keyCode: Int,
        action: Int,
        repeatCount: Int = 0
    ): PhysicalT9KeyHandler.KeyInput =
        PhysicalT9KeyHandler.KeyInput(
            keyCode = keyCode,
            action = action,
            repeatCount = repeatCount,
            downTime = 0L,
            eventTime = 0L
        )

    private fun state(
        mode: PhysicalT9KeyHandler.Mode = PhysicalT9KeyHandler.Mode.ENGLISH,
        isSmartEnglishActive: Boolean = true,
        hasPendingPunctuation: Boolean = false,
        pendingPunctuationOneKeyDeferred: Boolean = false,
        pendingPunctuationSet: PhysicalT9KeyHandler.PunctuationSet =
            PhysicalT9KeyHandler.PunctuationSet.ENGLISH,
        hasSmartEnglishCandidates: Boolean = false,
        heldPastLongPressDelay: Boolean = false
    ): PhysicalT9KeyFlow.State =
        PhysicalT9KeyFlow.State(
            mode = mode,
            isSmartEnglishActive = isSmartEnglishActive,
            hasPendingPunctuation = hasPendingPunctuation,
            pendingPunctuationOneKeyDeferred = pendingPunctuationOneKeyDeferred,
            pendingPunctuationSet = pendingPunctuationSet,
            hasSmartEnglishCandidates = hasSmartEnglishCandidates,
            heldPastLongPressDelay = heldPastLongPressDelay
        )
}

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
    fun smartEnglishDigitAppendsOnKeyUpOnly() {
        val flow = PhysicalT9KeyFlow()

        val down = flow.handle(input(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN), state())
        val up = flow.handle(input(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP), state())

        assertEquals(emptyList<PhysicalT9KeyFlow.Command>(), down?.commands)
        assertEquals(listOf(PhysicalT9KeyFlow.Command.AppendSmartEnglishDigit(2)), up?.commands)
    }

    @Test
    fun smartEnglishDigitLongPressCommitsShortcutAndSuppressesKeyUpDigit() {
        val flow = PhysicalT9KeyFlow()
        flow.handle(input(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN), state())

        val repeat = flow.handle(
            input(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN, repeatCount = 1),
            state(hasSmartEnglishCandidates = true, heldPastLongPressDelay = true)
        )
        val up = flow.handle(input(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP), state())

        assertEquals(
            listOf(PhysicalT9KeyFlow.Command.CommitSmartEnglishShortcut(KeyEvent.KEYCODE_2)),
            repeat?.commands
        )
        assertEquals(emptyList<PhysicalT9KeyFlow.Command>(), up?.commands)
    }

    @Test
    fun smartEnglishZeroCommitsCandidateOrMultiTapThenSpace() {
        val flow = PhysicalT9KeyFlow()
        flow.handle(input(KeyEvent.KEYCODE_0, KeyEvent.ACTION_DOWN), state())

        val up = flow.handle(input(KeyEvent.KEYCODE_0, KeyEvent.ACTION_UP), state())

        assertEquals(
            listOf(
                PhysicalT9KeyFlow.Command.CommitSmartEnglishCandidateOrMultiTap,
                PhysicalT9KeyFlow.Command.CommitPendingPunctuation,
                PhysicalT9KeyFlow.Command.CommitText(" "),
                PhysicalT9KeyFlow.Command.FlushEnglishLearningWord
            ),
            up?.commands
        )
    }

    @Test
    fun smartEnglishNavigationConsumesKeyUpThroughFlow() {
        val flow = PhysicalT9KeyFlow()

        val down = flow.handle(
            input(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN),
            state(hasSmartEnglishCandidates = true)
        )

        assertEquals(
            listOf(
                PhysicalT9KeyFlow.Command.OffsetBottomCandidatePage(
                    delta = -1
                )
            ),
            down?.commands
        )
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, down?.consumedKeyUp)
    }

    @Test
    fun smartEnglishOkConfirmsCandidateThroughFlow() {
        val flow = PhysicalT9KeyFlow()

        val down = flow.handle(
            input(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN),
            state(hasSmartEnglishCandidates = true)
        )

        assertEquals(
            listOf(PhysicalT9KeyFlow.Command.ConfirmSmartEnglishCandidate(hasPendingPunctuation = false)),
            down?.commands
        )
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, down?.consumedKeyUp)
    }

    @Test
    fun smartEnglishBackspaceWithoutCandidatesFallsThroughToApp() {
        val flow = PhysicalT9KeyFlow()

        assertNull(flow.handle(input(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN), state()))
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

        assertNull(flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN), state(mode = PhysicalT9KeyHandler.Mode.CHINESE)))
    }

    @Test
    fun simpleEnglishDigitUsesMultiTapOnKeyDown() {
        val flow = PhysicalT9KeyFlow()

        val down = flow.handle(
            input(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN),
            state(isSmartEnglishActive = false)
        )
        val up = flow.handle(
            input(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP),
            state(isSmartEnglishActive = false)
        )

        assertEquals(listOf(PhysicalT9KeyFlow.Command.HandleMultiTapKey(KeyEvent.KEYCODE_2)), down?.commands)
        assertEquals(emptyList<PhysicalT9KeyFlow.Command>(), up?.commands)
    }

    @Test
    fun simpleEnglishDigitLongPressCommitsLiteralDigitAndSuppressesKeyUp() {
        val flow = PhysicalT9KeyFlow()
        flow.handle(input(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN), state(isSmartEnglishActive = false))

        val repeat = flow.handle(
            input(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN, repeatCount = 1),
            state(isSmartEnglishActive = false, heldPastLongPressDelay = true)
        )
        val up = flow.handle(
            input(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP),
            state(isSmartEnglishActive = false)
        )

        assertEquals(
            listOf(
                PhysicalT9KeyFlow.Command.CancelMultiTapChar,
                PhysicalT9KeyFlow.Command.CommitText("2")
            ),
            repeat?.commands
        )
        assertEquals(emptyList<PhysicalT9KeyFlow.Command>(), up?.commands)
    }

    @Test
    fun simpleEnglishPoundCommitsPendingTextOrReturns() {
        val flow = PhysicalT9KeyFlow()
        flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_DOWN), state(isSmartEnglishActive = false))

        val up = flow.handle(input(KeyEvent.KEYCODE_POUND, KeyEvent.ACTION_UP), state(isSmartEnglishActive = false))

        assertEquals(listOf(PhysicalT9KeyFlow.Command.CommitEnglishPendingOrReturn), up?.commands)
    }

    @Test
    fun simpleEnglishStarShortAndLongPressStayInFlow() {
        val shortFlow = PhysicalT9KeyFlow()
        shortFlow.handle(input(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_DOWN), state(isSmartEnglishActive = false))
        val shortUp = shortFlow.handle(
            input(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_UP),
            state(isSmartEnglishActive = false)
        )

        val longFlow = PhysicalT9KeyFlow()
        longFlow.handle(input(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_DOWN), state(isSmartEnglishActive = false))
        val repeat = longFlow.handle(
            input(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_DOWN, repeatCount = 1),
            state(isSmartEnglishActive = false, heldPastLongPressDelay = true)
        )
        val longUp = longFlow.handle(
            input(KeyEvent.KEYCODE_STAR, KeyEvent.ACTION_UP),
            state(isSmartEnglishActive = false)
        )

        assertEquals(listOf(PhysicalT9KeyFlow.Command.HandleEnglishStarShortPress), shortUp?.commands)
        assertEquals(listOf(PhysicalT9KeyFlow.Command.HandleEnglishStarLongPress), repeat?.commands)
        assertEquals(emptyList<PhysicalT9KeyFlow.Command>(), longUp?.commands)
    }

    @Test
    fun simpleEnglishOkCommitsMultiTapAndConsumesKeyUp() {
        val flow = PhysicalT9KeyFlow()

        val down = flow.handle(
            input(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN),
            state(isSmartEnglishActive = false, hasMultiTapPendingChar = true)
        )
        val up = flow.handle(
            input(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP),
            state(isSmartEnglishActive = false)
        )

        assertEquals(listOf(PhysicalT9KeyFlow.Command.CommitMultiTapChar), down?.commands)
        assertEquals(emptyList<PhysicalT9KeyFlow.Command>(), up?.commands)
        assertEquals(true, up?.handled)
    }

    @Test
    fun simpleEnglishDeleteCancelsMultiTapWithoutConsumingKeyUp() {
        val flow = PhysicalT9KeyFlow()

        val down = flow.handle(
            input(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN),
            state(isSmartEnglishActive = false, hasMultiTapPendingChar = true)
        )

        assertEquals(listOf(PhysicalT9KeyFlow.Command.CancelMultiTapChar), down?.commands)
        assertNull(down?.consumedKeyUp)
    }

    @Test
    fun smartEnglishDeleteConsumesKeyUpWhenCandidatesExist() {
        val flow = PhysicalT9KeyFlow()

        val down = flow.handle(
            input(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN),
            state(hasSmartEnglishCandidates = true)
        )

        assertEquals(
            listOf(PhysicalT9KeyFlow.Command.SmartEnglishDelete(hasPendingPunctuation = false)),
            down?.commands
        )
        assertEquals(KeyEvent.KEYCODE_DEL, down?.consumedKeyUp)
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
        hasSmartEnglishDigits: Boolean = false,
        hasSmartEnglishCandidates: Boolean = false,
        hasMultiTapPendingChar: Boolean = false,
        heldPastLongPressDelay: Boolean = false
    ): PhysicalT9KeyFlow.State =
        PhysicalT9KeyFlow.State(
            mode = mode,
            isSmartEnglishActive = isSmartEnglishActive,
            hasPendingPunctuation = hasPendingPunctuation,
            pendingPunctuationOneKeyDeferred = pendingPunctuationOneKeyDeferred,
            pendingPunctuationSet = pendingPunctuationSet,
            hasSmartEnglishDigits = hasSmartEnglishDigits,
            hasSmartEnglishCandidates = hasSmartEnglishCandidates,
            hasMultiTapPendingChar = hasMultiTapPendingChar,
            heldPastLongPressDelay = heldPastLongPressDelay
        )
}

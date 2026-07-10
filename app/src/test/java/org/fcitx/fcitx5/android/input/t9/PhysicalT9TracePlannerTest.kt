/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalT9TracePlannerTest {
    @Test
    fun chineseForwardingWaitsForCandidateSourceFrame() {
        val plan = requireNotNull(
            PhysicalT9TracePlanner.plan(
                state = state(mode = PhysicalT9KeyHandler.Mode.CHINESE),
                decision = PhysicalT9KeyFlow.Decision(
                    handled = false,
                    consumedKeyUp = KeyEvent.KEYCODE_2
                ),
                forwardsThroughOuterRoute = true
            )
        )

        assertEquals("CHINESE/PINYIN/INPUT", plan.path)
        assertEquals(T9ResponsivenessTrace.CompletionKind.CANDIDATE_FRAME, plan.completionKind)
        assertTrue(plan.requiresSourceEvent)
    }

    @Test
    fun simpleEnglishMultiTapCompletesAtEditorEffect() {
        val plan = requireNotNull(
            PhysicalT9TracePlanner.plan(
                state = state(mode = PhysicalT9KeyHandler.Mode.ENGLISH),
                decision = decision(PhysicalT9KeyFlow.Command.HandleMultiTapKey(KeyEvent.KEYCODE_2)),
                forwardsThroughOuterRoute = false
            )
        )

        assertEquals("ENGLISH/TEXT_INPUT", plan.path)
        assertEquals(T9ResponsivenessTrace.CompletionKind.EFFECT, plan.completionKind)
    }

    @Test
    fun numberHintWaitsForInputSurfaceFrame() {
        val plan = requireNotNull(
            PhysicalT9TracePlanner.plan(
                state = state(mode = PhysicalT9KeyHandler.Mode.NUMBER),
                decision = decision(PhysicalT9KeyFlow.Command.ShowNumberOperatorHintPanel),
                forwardsThroughOuterRoute = false
            )
        )

        assertEquals("NUMBER/NUMBER_OPERATOR", plan.path)
        assertEquals(T9ResponsivenessTrace.CompletionKind.INPUT_SURFACE_FRAME, plan.completionKind)
    }

    @Test
    fun directReadingMoveOwnsSynchronousCandidateFrame() {
        val plan = requireNotNull(
            PhysicalT9TracePlanner.plan(
                state = state(mode = PhysicalT9KeyHandler.Mode.CHINESE),
                decision = decision(PhysicalT9KeyFlow.Command.MoveHighlightedReading(1)),
                forwardsThroughOuterRoute = false
            )
        )

        assertEquals("CHINESE/PINYIN/NAVIGATION", plan.path)
        assertEquals(T9ResponsivenessTrace.CompletionKind.CANDIDATE_FRAME, plan.completionKind)
        assertTrue(plan.candidateFrameIsSynchronous)
        assertFalse(plan.requiresSourceEvent)
    }

    @Test
    fun smartEnglishDigitUsesCandidateFrameWithoutEngineWait() {
        val plan = requireNotNull(
            PhysicalT9TracePlanner.plan(
                state = state(
                    mode = PhysicalT9KeyHandler.Mode.ENGLISH,
                    smartEnglish = true
                ),
                decision = decision(PhysicalT9KeyFlow.Command.AppendSmartEnglishDigit(2)),
                forwardsThroughOuterRoute = false
            )
        )

        assertEquals("SMART_ENGLISH/TEXT_INPUT", plan.path)
        assertEquals(T9ResponsivenessTrace.CompletionKind.CANDIDATE_FRAME, plan.completionKind)
        assertFalse(plan.candidateFrameIsSynchronous)
    }

    private fun decision(command: PhysicalT9KeyFlow.Command) = PhysicalT9KeyFlow.Decision(
        handled = true,
        commands = listOf(command)
    )

    private fun state(
        mode: PhysicalT9KeyHandler.Mode,
        smartEnglish: Boolean = false
    ) = PhysicalT9KeyFlow.State(
        mode = mode,
        isSmartEnglishActive = smartEnglish,
        chineseComposing = false,
        compositionKeyCount = 0,
        hasPendingPunctuation = false,
        hasSmartEnglishDigits = false,
        hasSmartEnglishCandidates = false,
        hasMultiTapPendingChar = false,
        hasTopReadingCandidates = false,
        hasBottomCandidateRow = false,
        candidateFocus = PhysicalT9KeyHandler.CandidateFocus.BOTTOM,
        heldPastLongPressDelay = false
    )
}

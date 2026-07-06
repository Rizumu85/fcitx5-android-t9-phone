/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9KeyEffectPlannerTest {

    private val planner = T9KeyEffectPlanner()

    @Test
    fun chineseUpKeyMovesToTopPinyinWhenTopCandidatesExist() {
        val effect = planner.planChineseCandidateFocusNavigation(
            keyInput(KeyEvent.KEYCODE_DPAD_UP),
            snapshot(
                mode = PhysicalT9KeyHandler.Mode.CHINESE,
                hasTopPinyinCandidates = true,
                candidateFocus = PhysicalT9KeyHandler.CandidateFocus.BOTTOM
            )
        )

        assertEquals(
            T9KeyEffectPlanner.Effect.MoveCandidateFocus(PhysicalT9KeyHandler.CandidateFocus.TOP),
            effect
        )
        assertTrue(effect.consumeKeyUp)
    }

    @Test
    fun chineseBottomOkConfirmsBottomCandidateAndConsumesKeyUp() {
        val effect = planner.planChineseCandidateFocusNavigation(
            keyInput(KeyEvent.KEYCODE_DPAD_CENTER),
            snapshot(
                mode = PhysicalT9KeyHandler.Mode.CHINESE,
                candidateFocus = PhysicalT9KeyHandler.CandidateFocus.BOTTOM
            )
        )

        assertEquals(
            T9KeyEffectPlanner.Effect.CommitHighlightedBottomCandidate,
            effect
        )
        assertTrue(effect.consumeKeyUp)
    }

    private fun keyInput(keyCode: Int): PhysicalT9KeyHandler.KeyInput =
        PhysicalT9KeyHandler.KeyInput(
            keyCode = keyCode,
            action = KeyEvent.ACTION_DOWN,
            repeatCount = 0,
            downTime = 0L,
            eventTime = 0L
        )

    private fun snapshot(
        mode: PhysicalT9KeyHandler.Mode,
        hasTopPinyinCandidates: Boolean = false,
        candidateFocus: PhysicalT9KeyHandler.CandidateFocus =
            PhysicalT9KeyHandler.CandidateFocus.BOTTOM
    ): T9KeyEffectPlanner.Snapshot = T9KeyEffectPlanner.Snapshot(
        mode = mode,
        hasTopPinyinCandidates = hasTopPinyinCandidates,
        candidateFocus = candidateFocus
    )
}

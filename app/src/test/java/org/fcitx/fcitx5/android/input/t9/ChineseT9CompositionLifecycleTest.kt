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

class ChineseT9CompositionLifecycleTest {

    @Test
    fun forwardedDigitUpdatesSessionAndRequestsCandidateRefresh() {
        val session = ChineseT9CompositionSession()
        val lifecycle = ChineseT9CompositionLifecycle(session)

        val action = lifecycle.handleForwardedKeyDown(KeyEvent.KEYCODE_6)

        assertEquals(
            ChineseT9CompositionLifecycle.ForwardedKeyAction.REFRESH_AFTER_ENGINE_CANDIDATES,
            action
        )
        assertEquals("6", session.rawSequence())
    }

    @Test
    fun deletingFinalKeyRequestsImmediateCandidateHide() {
        val session = ChineseT9CompositionSession()
        val lifecycle = ChineseT9CompositionLifecycle(session)
        session.appendDigit('6')

        val action = lifecycle.handleForwardedKeyDown(KeyEvent.KEYCODE_DEL)

        assertEquals(
            ChineseT9CompositionLifecycle.ForwardedKeyAction.HIDE_CANDIDATE_UI_IMMEDIATELY,
            action
        )
        assertFalse(session.hasState())
    }

    @Test
    fun editorTapClearDependsOnCompositionState() {
        val session = ChineseT9CompositionSession()
        val lifecycle = ChineseT9CompositionLifecycle(session)

        assertFalse(
            lifecycle.shouldClearFromEditorTap(
                isActive = true,
                state = ChineseT9CompositionLifecycle.InputState.IDLE
            )
        )

        session.appendDigit('6')

        assertTrue(
            lifecycle.shouldClearFromEditorTap(
                isActive = true,
                state = ChineseT9CompositionLifecycle.InputState.IDLE
            )
        )
    }

    @Test
    fun hiddenCompositionClearWaitsForNoPendingPunctuationAndNoKeys() {
        val session = ChineseT9CompositionSession()
        val lifecycle = ChineseT9CompositionLifecycle(session)

        assertTrue(
            lifecycle.shouldClearHiddenComposition(
                isActive = true,
                hasPendingPunctuation = false
            )
        )

        session.appendDigit('6')

        assertFalse(
            lifecycle.shouldClearHiddenComposition(
                isActive = true,
                hasPendingPunctuation = false
            )
        )
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalBottomCandidateKeyFlowTest {
    private val flow = PhysicalBottomCandidateKeyFlow { 500 }

    @Test
    fun mapsAllNavigationAndConfirmKeysThroughOneCandidateContract() {
        assertEquals(
            PhysicalBottomCandidateKeyFlow.Command.Move(-1),
            down(KeyEvent.KEYCODE_DPAD_LEFT).command
        )
        assertEquals(
            PhysicalBottomCandidateKeyFlow.Command.Move(1),
            down(KeyEvent.KEYCODE_DPAD_RIGHT).command
        )
        assertEquals(
            PhysicalBottomCandidateKeyFlow.Command.OffsetPage(-1),
            down(KeyEvent.KEYCODE_DPAD_UP).command
        )
        assertEquals(
            PhysicalBottomCandidateKeyFlow.Command.OffsetPage(1),
            down(KeyEvent.KEYCODE_DPAD_DOWN).command
        )
        assertEquals(
            PhysicalBottomCandidateKeyFlow.Command.CommitCurrent,
            down(KeyEvent.KEYCODE_DPAD_CENTER).command
        )
    }

    @Test
    fun zeroSeparatesShortConfirmFromLongShortcutTen() {
        down(KeyEvent.KEYCODE_0)
        assertEquals(
            PhysicalBottomCandidateKeyFlow.Command.CommitCurrent,
            up(KeyEvent.KEYCODE_0).command
        )

        down(KeyEvent.KEYCODE_0)
        assertEquals(
            PhysicalBottomCandidateKeyFlow.Command.CommitShortcut(9),
            down(KeyEvent.KEYCODE_0, repeatCount = 1, eventTime = 600L).command
        )
        assertNull(up(KeyEvent.KEYCODE_0).command)
    }

    private fun down(
        keyCode: Int,
        repeatCount: Int = 0,
        eventTime: Long = 0L
    ) = requireNotNull(
        flow.handle(
            input(keyCode, KeyEvent.ACTION_DOWN, repeatCount, eventTime)
        )
    )

    private fun up(keyCode: Int) = requireNotNull(
        flow.handle(input(keyCode, KeyEvent.ACTION_UP))
    )

    private fun input(
        keyCode: Int,
        action: Int,
        repeatCount: Int = 0,
        eventTime: Long = 0L
    ) = PhysicalBottomCandidateKeyFlow.Input(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        downTime = 0L,
        eventTime = eventTime,
        hasCandidates = true
    )
}

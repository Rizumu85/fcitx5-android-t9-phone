/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class T9CandidatePanelWidthStateTest {
    @Test
    fun tracksCurrentFrameWidth() {
        val state = T9CandidatePanelWidthState()

        assertEquals(120, state.update(120))
        assertEquals(40, state.update(40))
        assertEquals(180, state.update(180))
    }

    @Test
    fun ignoresNonPositiveMeasurements() {
        val state = T9CandidatePanelWidthState()

        assertEquals(0, state.update(0))
        assertNull(state.currentOrNull())
        assertEquals(90, state.update(90))
        assertEquals(90, state.update(-1))
    }

    @Test
    fun resetStartsANewSession() {
        val state = T9CandidatePanelWidthState()

        state.update(160)
        state.reset()

        assertNull(state.currentOrNull())
        assertEquals(30, state.update(30))
    }
}

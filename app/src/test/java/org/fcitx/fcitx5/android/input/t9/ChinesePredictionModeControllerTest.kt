/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChinesePredictionModeControllerTest {
    @Test
    fun togglePersistsAndAppliesTheNewMode() {
        val persisted = mutableListOf<Boolean>()
        val applied = mutableListOf<Boolean>()
        val indicators = mutableListOf<Boolean>()
        val controller = ChinesePredictionModeController(
            initialEnabled = false,
            setPreference = persisted::add,
            onEnabledChanged = applied::add,
            showModeIndicator = indicators::add
        )

        controller.toggle()

        assertTrue(controller.enabled)
        assertEquals(listOf(true), persisted)
        assertEquals(listOf(true), applied)
        assertEquals(listOf(true), indicators)
    }

    @Test
    fun unchangedPreferenceCallbackDoesNotApplyTwice() {
        val applied = mutableListOf<Boolean>()
        val controller = ChinesePredictionModeController(
            initialEnabled = true,
            setPreference = {},
            onEnabledChanged = applied::add,
            showModeIndicator = {}
        )

        controller.onPreferenceChanged(true)
        controller.onPreferenceChanged(false)

        assertFalse(controller.enabled)
        assertEquals(listOf(false), applied)
    }
}

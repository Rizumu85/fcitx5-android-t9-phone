/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEnglishT9ModeControllerTest {

    @Test
    fun preferenceChangeUpdatesEnabledStateAndCoordinator() {
        val events = mutableListOf<String>()
        val controller = controller(
            initialEnabled = false,
            events = events
        )

        controller.onPreferenceChanged(true)

        assertTrue(controller.enabled)
        assertEquals(listOf("enabled:true"), events)
    }

    @Test
    fun toggleWritesPreferenceRefreshesCoordinatorAndShowsModeBadge() {
        val events = mutableListOf<String>()
        val controller = controller(
            initialEnabled = false,
            events = events
        )

        controller.toggle()

        assertTrue(controller.enabled)
        assertEquals(
            listOf(
                "preference:true",
                "enabled:true",
                "badge:T9"
            ),
            events
        )

        controller.toggle()

        assertFalse(controller.enabled)
        assertEquals(
            listOf(
                "preference:true",
                "enabled:true",
                "badge:T9",
                "preference:false",
                "enabled:false",
                "badge:abc"
            ),
            events
        )
    }

    private fun controller(
        initialEnabled: Boolean,
        events: MutableList<String>
    ): SmartEnglishT9ModeController =
        SmartEnglishT9ModeController(
            initialEnabled = initialEnabled,
            setPreference = { events += "preference:$it" },
            onEnabledChanged = { events += "enabled:$it" },
            showModeIndicator = { events += "badge:$it" }
        )
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartEnglishCaseCoordinatorTest {

    @Test
    fun shortPressRefreshesPendingMultiTapComposition() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            events = events,
            pendingDisplay = "B",
            caseLabel = "Abc"
        )

        coordinator.handleShortPress()

        assertEquals(listOf("shift", "compose:B"), events)
    }

    @Test
    fun longPressShowsCaseBadgeWhenNoPendingMultiTapChar() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            events = events,
            pendingDisplay = null,
            caseLabel = "ABC"
        )

        coordinator.handleLongPress()

        assertEquals(listOf("caps", "badge:ABC"), events)
    }

    private fun coordinator(
        events: MutableList<String>,
        pendingDisplay: String?,
        caseLabel: String
    ): SmartEnglishCaseCoordinator =
        SmartEnglishCaseCoordinator(
            toggleShiftOnce = { events += "shift" },
            toggleCaps = { events += "caps" },
            pendingMultiTapDisplay = { pendingDisplay },
            setComposingText = { events += "compose:$it" },
            caseLabel = { caseLabel },
            showModeIndicator = { events += "badge:$it" }
        )
}

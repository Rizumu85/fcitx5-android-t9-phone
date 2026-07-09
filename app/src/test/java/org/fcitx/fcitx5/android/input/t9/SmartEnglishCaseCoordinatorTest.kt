/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartEnglishCaseCoordinatorTest {

    @Test
    fun cycleRefreshesPendingMultiTapComposition() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            events = events,
            pendingDisplay = "B",
            caseLabel = "Abc"
        )

        coordinator.cycle()

        assertEquals(listOf("cycle", "compose:B"), events)
    }

    @Test
    fun cycleShowsCaseBadgeWhenNoPendingMultiTapChar() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            events = events,
            pendingDisplay = null,
            caseLabel = "ABC"
        )

        coordinator.cycle()

        assertEquals(listOf("cycle", "badge:ABC"), events)
    }

    private fun coordinator(
        events: MutableList<String>,
        pendingDisplay: String?,
        caseLabel: String
    ): SmartEnglishCaseCoordinator =
        SmartEnglishCaseCoordinator(
            cycleCase = { events += "cycle" },
            pendingMultiTapDisplay = { pendingDisplay },
            setComposingText = { events += "compose:$it" },
            caseLabel = { caseLabel },
            showModeIndicator = { events += "badge:$it" }
        )
}

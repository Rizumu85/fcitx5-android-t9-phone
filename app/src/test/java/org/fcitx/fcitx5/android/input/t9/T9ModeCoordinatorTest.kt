/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9ModeCoordinatorTest {

    @Test
    fun switchCyclesModesAndPublishesLabelsAfterCleanup() {
        val events = mutableListOf<String>()
        val coordinator = T9ModeCoordinator(
            beforeModeChange = { events += "cleanup" },
            onEnglishModeEntered = { events += "english" },
            onModeLabelChanged = { events += "label:$it" },
            showModeIndicator = { events += "badge:$it" }
        )

        coordinator.switchToNextMode()
        coordinator.switchToNextMode()
        coordinator.switchToNextMode()

        assertEquals(T9InputMode.CHINESE, coordinator.current)
        assertEquals(
            listOf(
                "cleanup",
                "english",
                "label:En",
                "badge:En",
                "cleanup",
                "label:123",
                "badge:123",
                "cleanup",
                "label:中",
                "badge:中"
            ),
            events
        )
    }
}

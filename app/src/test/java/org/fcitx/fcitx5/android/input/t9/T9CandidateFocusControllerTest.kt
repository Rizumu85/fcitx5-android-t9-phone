/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9CandidateFocusControllerTest {

    @Test
    fun moveNotifiesAndResetReturnsToBottomWithoutNotification() {
        var notifications = 0
        val controller = T9CandidateFocusController(
            onFocusChanged = { notifications += 1 }
        )

        controller.moveTo(T9CandidateFocus.TOP)

        assertEquals(T9CandidateFocus.TOP, controller.current)
        assertEquals(1, notifications)

        controller.moveTo(T9CandidateFocus.TOP)

        assertEquals(T9CandidateFocus.TOP, controller.current)
        assertEquals(1, notifications)

        controller.reset()

        assertEquals(T9CandidateFocus.BOTTOM, controller.current)
        assertEquals(1, notifications)
    }
}

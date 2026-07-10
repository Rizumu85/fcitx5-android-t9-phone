/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FcitxEventTest {
    @Test
    fun nativeRimeAvailabilityOrdinalMatchesKotlinEventContract() {
        val event = FcitxEvent.create(
            type = 11,
            params = arrayOf(2, "t9_zhuyin")
        ) as FcitxEvent.RimeAvailabilityEvent

        assertEquals(FcitxEvent.RimeAvailabilityEvent.State.Ready, event.data.state)
        assertEquals("t9_zhuyin", event.data.activeSchema)
    }
}

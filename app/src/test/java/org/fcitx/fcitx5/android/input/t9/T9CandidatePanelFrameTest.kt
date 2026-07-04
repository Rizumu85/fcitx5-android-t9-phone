/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidatePanelFrameTest {

    @Test
    fun shortcutRowReservesEnoughHeightForTwoLineCandidates() {
        val oneLine = T9CandidatePanelFrame.oneLineRowHeight(
            fontSizeSp = 20,
            scaledDensity = 2f,
            verticalPaddingPx = 6
        )
        val shortcut = T9CandidatePanelFrame.shortcutRowHeight(
            fontSizeSp = 20,
            scaledDensity = 2f,
            verticalPaddingPx = 6
        )

        assertTrue(shortcut > oneLine)
        assertEquals(94, shortcut)
    }
}

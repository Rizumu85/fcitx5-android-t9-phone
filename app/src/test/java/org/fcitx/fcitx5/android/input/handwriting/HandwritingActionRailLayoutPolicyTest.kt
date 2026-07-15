/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingActionRailLayoutPolicyTest {
    @Test
    fun keepsPreferredGeometryWhenHeightAllowsIt() {
        assertEquals(
            HandwritingActionRailSizing(buttonSizePx = 40, verticalMarginPx = 4),
            resolve(availableHeightPx = 240)
        )
    }

    @Test
    fun landscapeHeightShrinksButtonsAndKeepsVisibleSpacing() {
        assertEquals(
            HandwritingActionRailSizing(buttonSizePx = 36, verticalMarginPx = 2),
            resolve(availableHeightPx = 160)
        )
    }

    @Test
    fun landscapeHeightAfterEdgeInsetsStillFitsEveryAction() {
        val sizing = resolve(availableHeightPx = 156)

        assertEquals(HandwritingActionRailSizing(buttonSizePx = 35, verticalMarginPx = 2), sizing)
        assertTrue(sizing.occupiedHeight(ButtonCount) <= 156)
    }

    @Test
    fun extremeHeightNeverPlacesActionsOutsideTheRail() {
        val sizing = resolve(availableHeightPx = 120)

        assertEquals(HandwritingActionRailSizing(buttonSizePx = 30, verticalMarginPx = 0), sizing)
        assertTrue(sizing.occupiedHeight(ButtonCount) <= 120)
    }

    private fun resolve(availableHeightPx: Int) = HandwritingActionRailLayoutPolicy.resolve(
        availableHeightPx = availableHeightPx,
        buttonCount = ButtonCount,
        preferredButtonSizePx = 40,
        preferredMarginPx = 4,
        minimumButtonSizePx = 32,
        minimumMarginPx = 2
    )

    private companion object {
        const val ButtonCount = 4
    }
}

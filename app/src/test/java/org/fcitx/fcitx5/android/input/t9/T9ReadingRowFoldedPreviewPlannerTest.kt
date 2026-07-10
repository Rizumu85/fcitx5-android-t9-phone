/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9ReadingRowFoldedPreviewPlannerTest {
    @Test
    fun usesEveryWholeChipThatFitsTheStableViewport() {
        val plan = T9ReadingRowFoldedPreviewPlanner.plan(
            itemCount = 10,
            minVisibleCount = 4,
            viewportWidthPx = 300,
            chipWidthsPx = List(10) { 40 },
            chipSpacingPx = 4,
            overflowHintSpacingPx = 4,
            overflowHintEndReservePx = 22
        )

        assertEquals(6, plan.visibleCount)
        assertEquals(260, plan.chipContentWidthPx)
        assertEquals(264, plan.overflowHintStartPx)
    }

    @Test
    fun preservesTheFourChipMinimumForAShortCandidatePage() {
        val plan = T9ReadingRowFoldedPreviewPlanner.plan(
            itemCount = 7,
            minVisibleCount = 4,
            viewportWidthPx = 194,
            chipWidthsPx = List(7) { 40 },
            chipSpacingPx = 4,
            overflowHintSpacingPx = 4,
            overflowHintEndReservePx = 22
        )

        assertEquals(4, plan.visibleCount)
        assertEquals(172, plan.chipContentWidthPx)
        assertEquals(176, plan.overflowHintStartPx)
    }
}

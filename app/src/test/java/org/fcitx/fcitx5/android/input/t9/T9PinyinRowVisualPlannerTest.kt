/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9PinyinRowVisualPlannerTest {
    @Test
    fun foldedHintKeepsItsOwnPositionAfterVisibleChips() {
        val plan = T9PinyinRowVisualPlanner.plan(
            input(
                rowPlan = T9PinyinOverflowPolicy.Plan(
                    folded = true,
                    showHint = true,
                    visibleCount = 4
                )
            )
        )

        assertEquals(listOf("gei", "hei", "ge", "he"), plan.displayedItems)
        assertTrue(plan.showOverflowHint)
        assertEquals(128, plan.pinyinBarWidthPx)
        assertEquals(132, plan.overflowHintStartPx)
    }

    @Test
    fun focusedFoldedRowKeepsAllChipsForScrollAnchoring() {
        val plan = T9PinyinRowVisualPlanner.plan(
            input(
                rowPlan = T9PinyinOverflowPolicy.Plan(
                    folded = true,
                    showHint = false,
                    visibleCount = 7
                )
            )
        )

        assertEquals(listOf("gei", "hei", "ge", "he", "g", "h", "i"), plan.displayedItems)
        assertFalse(plan.showOverflowHint)
        assertEquals(150, plan.pinyinBarWidthPx)
        assertEquals(0, plan.overflowHintStartPx)
        assertFalse(plan.usesWindowedDisplay)
    }

    private fun input(
        rowPlan: T9PinyinOverflowPolicy.Plan?
    ): T9PinyinRowVisualPlanner.Input =
        T9PinyinRowVisualPlanner.Input(
            state = T9PinyinRowWindow.VisibleState(
                items = listOf("gei", "hei", "ge", "he", "g", "h", "i"),
                highlightedIndex = 0,
                windowStart = 0
            ),
            rowPlan = rowPlan,
            rowWidthPx = 150,
            widths = T9PinyinRowWidthCalculator.Widths(
                fullContentWidthPx = 162,
                foldedChipContentWidthPx = 128,
                overflowHintStartPx = 132,
                foldedContentWidthPx = 150
            ),
            chipWidthsPx = listOf(34, 34, 24, 24, 14, 14, 14),
            chipSpacingPx = 4
        )
}

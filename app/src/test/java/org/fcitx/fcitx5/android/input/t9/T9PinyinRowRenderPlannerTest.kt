/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9PinyinRowRenderPlannerTest {
    @Test
    fun fullRowShowsEveryItemWithoutHint() {
        val plan = T9PinyinRowRenderPlanner.plan(
            state = state(items = listOf("gei", "hei", "ge"), highlightedIndex = 1),
            rowPlan = T9PinyinOverflowPolicy.Plan(
                folded = false,
                showHint = false,
                visibleCount = 3
            )
        )

        assertEquals(listOf("gei", "hei", "ge"), plan.displayedItems)
        assertEquals(1, plan.displayedHighlight)
        assertFalse(plan.showOverflowHint)
    }

    @Test
    fun foldedRowShowsVisibleCountAndHint() {
        val plan = T9PinyinRowRenderPlanner.plan(
            state = state(items = listOf("gei", "hei", "ge", "he", "g"), highlightedIndex = 0),
            rowPlan = T9PinyinOverflowPolicy.Plan(
                folded = true,
                showHint = true,
                visibleCount = 4
            )
        )

        assertEquals(listOf("gei", "hei", "ge", "he"), plan.displayedItems)
        assertTrue(plan.showOverflowHint)
    }

    @Test
    fun highlightIsClampedToDisplayedItems() {
        val plan = T9PinyinRowRenderPlanner.plan(
            state = state(items = listOf("gei", "hei", "ge", "he", "g"), highlightedIndex = 4),
            rowPlan = T9PinyinOverflowPolicy.Plan(
                folded = true,
                showHint = true,
                visibleCount = 4
            )
        )

        assertEquals(3, plan.displayedHighlight)
    }

    private fun state(
        items: List<String>,
        highlightedIndex: Int
    ): T9PinyinRowWindow.VisibleState =
        T9PinyinRowWindow.VisibleState(
            items = items,
            highlightedIndex = highlightedIndex,
            windowStart = 0
        )
}

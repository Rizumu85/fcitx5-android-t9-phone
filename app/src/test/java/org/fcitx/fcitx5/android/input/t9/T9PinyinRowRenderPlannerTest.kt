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
    fun emptyRowHasNoDisplayedItems() {
        val plan = T9PinyinRowRenderPlanner.plan(
            state = state(items = emptyList(), highlightedIndex = 0),
            rowPlan = null
        )

        assertEquals(emptyList<String>(), plan.displayedItems)
        assertEquals(0, plan.displayedHighlight)
        assertFalse(plan.usesWindowedDisplay)
    }

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
        assertFalse(plan.usesWindowedDisplay)
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
        assertTrue(plan.usesWindowedDisplay)
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

    @Test
    fun focusedFoldedRowShowsOnlyWholeChipsThatFit() {
        val plan = T9PinyinRowRenderPlanner.plan(
            state = state(items = listOf("gei", "hei", "ge", "he", "g", "h"), highlightedIndex = 0),
            rowPlan = T9PinyinOverflowPolicy.Plan(
                folded = true,
                showHint = false,
                visibleCount = 6
            ),
            focusedViewportWidthPx = 100,
            chipWidthsPx = listOf(34, 34, 24, 24, 14, 14),
            chipSpacingPx = 4
        )

        assertEquals(listOf("gei", "hei", "ge"), plan.displayedItems)
        assertEquals(0, plan.displayedHighlight)
        assertTrue(plan.usesWindowedDisplay)
    }

    @Test
    fun focusedFoldedRowSlidesWindowWhenHighlightMovesPastVisibleChips() {
        val plan = T9PinyinRowRenderPlanner.plan(
            state = state(items = listOf("gei", "hei", "ge", "he", "g", "h"), highlightedIndex = 5),
            rowPlan = T9PinyinOverflowPolicy.Plan(
                folded = true,
                showHint = false,
                visibleCount = 6
            ),
            focusedViewportWidthPx = 100,
            chipWidthsPx = listOf(34, 34, 24, 24, 14, 14),
            chipSpacingPx = 4
        )

        assertEquals(listOf("ge", "he", "g", "h"), plan.displayedItems)
        assertEquals(3, plan.displayedHighlight)
        assertTrue(plan.usesWindowedDisplay)
    }

    @Test
    fun focusedFoldedRowKeepsRightEdgeGuardForScaledHighlight() {
        val plan = T9PinyinRowRenderPlanner.plan(
            state = state(items = listOf("gei", "hei", "ge", "he", "g", "h"), highlightedIndex = 0),
            rowPlan = T9PinyinOverflowPolicy.Plan(
                folded = true,
                showHint = false,
                visibleCount = 6
            ),
            focusedViewportWidthPx = 150,
            focusedEdgeGuardPx = 12,
            chipWidthsPx = listOf(34, 34, 24, 24, 14, 14),
            chipSpacingPx = 4
        )

        assertEquals(listOf("gei", "hei", "ge", "he"), plan.displayedItems)
        assertTrue(plan.usesWindowedDisplay)
    }

    @Test
    fun focusedFoldedRowRevealsHiddenChipsOneStepAtATime() {
        val items = listOf("gei", "hei", "ge", "he", "g", "h", "i")

        val hPlan = T9PinyinRowRenderPlanner.plan(
            state = state(items = items, highlightedIndex = 5),
            rowPlan = T9PinyinOverflowPolicy.Plan(
                folded = true,
                showHint = false,
                visibleCount = items.size
            ),
            focusedViewportWidthPx = 150,
            chipWidthsPx = listOf(34, 34, 24, 24, 14, 14, 14),
            chipSpacingPx = 4
        )
        val iPlan = T9PinyinRowRenderPlanner.plan(
            state = state(items = items, highlightedIndex = 6),
            rowPlan = T9PinyinOverflowPolicy.Plan(
                folded = true,
                showHint = false,
                visibleCount = items.size
            ),
            focusedViewportWidthPx = 150,
            chipWidthsPx = listOf(34, 34, 24, 24, 14, 14, 14),
            chipSpacingPx = 4
        )

        assertEquals(listOf("hei", "ge", "he", "g", "h"), hPlan.displayedItems)
        assertEquals(4, hPlan.displayedHighlight)
        assertEquals(listOf("hei", "ge", "he", "g", "h", "i"), iPlan.displayedItems)
        assertEquals(5, iPlan.displayedHighlight)
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

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9PinyinRowSurfacePlannerTest {
    @Test
    fun hiddenRowHasNoSurface() {
        assertNull(
            T9PinyinRowSurfacePlanner.plan(
                input(state = null)
            )
        )
    }

    @Test
    fun foldedIdleRowKeepsEllipsisAndFoldedBubbleWidth() {
        val plan = requireNotNull(
            T9PinyinRowSurfacePlanner.plan(
                input(candidateMeasuredWidthPx = 80)
            )
        )

        assertTrue(plan.contentReady)
        assertEquals(150, plan.rowWidthPx)
        assertEquals(listOf("gei", "hei", "ge", "he"), plan.displayedItems)
        assertTrue(plan.showOverflowHint)
        assertEquals(132, plan.overflowHintStartPx)
        assertEquals(128, plan.pinyinBarWidthPx)
    }

    @Test
    fun foldedFocusedRowKeepsStableViewportAndWholeChipWindow() {
        val plan = requireNotNull(
            T9PinyinRowSurfacePlanner.plan(
                input(
                    candidateMeasuredWidthPx = 80,
                    focused = true
                )
            )
        )

        assertTrue(plan.contentReady)
        assertEquals(150, plan.rowWidthPx)
        assertEquals(listOf("gei", "hei", "ge", "he", "g"), plan.displayedItems)
        assertFalse(plan.showOverflowHint)
        assertEquals(150, plan.pinyinBarWidthPx)
        assertTrue(plan.usesWindowedDisplay)
    }

    @Test
    fun visualPlanExistsBeforeCandidateWidthIsReady() {
        val plan = requireNotNull(
            T9PinyinRowSurfacePlanner.plan(
                input(
                    candidateMeasuredWidthPx = null,
                    fallbackViewportWidthPx = 150
                )
            )
        )

        assertFalse(plan.contentReady)
        assertNull(plan.rowWidthPx)
        assertEquals(150, plan.pinyinBarWidthPx)
    }

    private fun input(
        candidateMeasuredWidthPx: Int? = 80,
        fallbackViewportWidthPx: Int? = null,
        state: T9PinyinRowWindow.VisibleState? = T9PinyinRowWindow.VisibleState(
            items = listOf("gei", "hei", "ge", "he", "g", "h", "i"),
            highlightedIndex = 0,
            windowStart = 0
        ),
        focused: Boolean = false
    ): T9PinyinRowSurfacePlanner.Input =
        T9PinyinRowSurfacePlanner.Input(
            candidateMeasuredWidthPx = candidateMeasuredWidthPx,
            fallbackViewportWidthPx = fallbackViewportWidthPx,
            state = state,
            widths = T9PinyinRowWidthCalculator.Widths(
                fullContentWidthPx = 260,
                foldedChipContentWidthPx = 128,
                overflowHintStartPx = 132,
                foldedContentWidthPx = 150
            ),
            chipWidthsPx = listOf(34, 34, 24, 24, 14, 14, 14),
            chipSpacingPx = 4,
            maxRowWidthPx = 320,
            minVisibleChips = 4,
            focused = focused
        )
}

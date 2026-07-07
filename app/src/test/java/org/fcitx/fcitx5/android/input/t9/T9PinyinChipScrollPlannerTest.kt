/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9PinyinChipScrollPlannerTest {
    @Test
    fun hiddenTrailingChipScrollsToCleanLeadingChipBoundary() {
        val plan = T9PinyinChipScrollPlanner.plan(
            currentScrollX = 0,
            viewportWidthPx = 150,
            contentWidthPx = 176,
            itemBounds = bounds,
            highlightedIndex = 5
        )

        assertEquals(26, plan.scrollX)
    }

    @Test
    fun visibleChipKeepsCurrentScroll() {
        val plan = T9PinyinChipScrollPlanner.plan(
            currentScrollX = 20,
            viewportWidthPx = 150,
            contentWidthPx = 176,
            itemBounds = bounds,
            highlightedIndex = 2
        )

        assertEquals(20, plan.scrollX)
    }

    @Test
    fun leadingHiddenChipScrollsBackToItsStart() {
        val plan = T9PinyinChipScrollPlanner.plan(
            currentScrollX = 90,
            viewportWidthPx = 150,
            contentWidthPx = 176,
            itemBounds = bounds,
            highlightedIndex = 1
        )

        assertEquals(26, plan.scrollX)
    }

    @Test
    fun finalChipIsCappedByAvailableScrollRangeWithoutChangingLayoutWidth() {
        val plan = T9PinyinChipScrollPlanner.plan(
            currentScrollX = 38,
            viewportWidthPx = 150,
            contentWidthPx = 198,
            itemBounds = bounds + T9PinyinChipScrollPlanner.ItemBounds(176, 198),
            highlightedIndex = 6
        )

        assertEquals(48, plan.scrollX)
    }

    private val bounds = listOf(
        T9PinyinChipScrollPlanner.ItemBounds(0, 38),
        T9PinyinChipScrollPlanner.ItemBounds(38, 76),
        T9PinyinChipScrollPlanner.ItemBounds(76, 104),
        T9PinyinChipScrollPlanner.ItemBounds(104, 132),
        T9PinyinChipScrollPlanner.ItemBounds(132, 154),
        T9PinyinChipScrollPlanner.ItemBounds(154, 176)
    )
}

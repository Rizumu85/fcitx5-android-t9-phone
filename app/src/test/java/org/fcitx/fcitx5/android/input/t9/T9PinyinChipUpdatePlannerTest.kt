/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9PinyinChipUpdatePlannerTest {

    @Test
    fun unchangedListDoesNotBindOrStyleChips() {
        val plan = T9PinyinChipUpdatePlanner.plan(
            oldItems = listOf("ni", "mi"),
            newItems = listOf("ni", "mi"),
            oldHighlightedIndex = 0,
            newHighlightedIndex = 0
        )

        assertFalse(plan.changed)
        assertEquals(emptyList<Int>(), plan.bindIndices)
        assertEquals(emptyList<Int>(), plan.styleIndices)
    }

    @Test
    fun changedMiddleItemOnlyBindsChangedItemAndVisibleLastBoundary() {
        val plan = T9PinyinChipUpdatePlanner.plan(
            oldItems = listOf("ni", "mi", "li"),
            newItems = listOf("ni", "ma", "li"),
            oldHighlightedIndex = 0,
            newHighlightedIndex = 0
        )

        assertTrue(plan.changed)
        assertEquals(listOf(1, 2), plan.bindIndices)
        assertEquals(listOf(1, 2, 0), plan.styleIndices)
    }

    @Test
    fun shrinkingListRebindsNewLastChipForMarginWithoutRecreatingAllChips() {
        val plan = T9PinyinChipUpdatePlanner.plan(
            oldItems = listOf("ni", "mi", "li", "ji"),
            newItems = listOf("ni", "mi"),
            oldHighlightedIndex = 3,
            newHighlightedIndex = 1
        )

        assertTrue(plan.changed)
        assertEquals(listOf(1), plan.bindIndices)
        assertEquals(listOf(1), plan.styleIndices)
    }

    @Test
    fun growingListBindsOnlyNewChipsAndPreviousLastBoundary() {
        val plan = T9PinyinChipUpdatePlanner.plan(
            oldItems = listOf("ni", "mi"),
            newItems = listOf("ni", "mi", "li", "ji"),
            oldHighlightedIndex = 0,
            newHighlightedIndex = 0
        )

        assertTrue(plan.changed)
        assertEquals(listOf(2, 3, 1), plan.bindIndices)
        assertEquals(listOf(2, 3, 1, 0), plan.styleIndices)
    }
}

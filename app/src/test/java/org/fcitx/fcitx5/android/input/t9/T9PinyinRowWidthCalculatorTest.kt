/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class T9PinyinRowWidthCalculatorTest {
    @Test
    fun emptyItemsHaveNoWidths() {
        assertNull(T9PinyinRowWidthCalculator.calculate(input(emptyList())))
    }

    @Test
    fun fullWidthUsesMarginsBetweenChipsOnly() {
        val widths = requireNotNull(
            T9PinyinRowWidthCalculator.calculate(input(listOf("a", "bb")))
        )

        assertEquals(50, widths.fullContentWidthPx)
    }

    @Test
    fun foldedWidthReservesHintMarginAndSafety() {
        val widths = requireNotNull(
            T9PinyinRowWidthCalculator.calculate(input(listOf("a", "bb", "ccc")))
        )

        assertEquals(72, widths.foldedContentWidthPx)
    }

    private fun input(items: List<String>): T9PinyinRowWidthCalculator.Input =
        T9PinyinRowWidthCalculator.Input(
            items = items,
            minVisibleChips = 2,
            chipHorizontalPaddingPx = 4,
            chipSpacingPx = 4,
            overflowHintTextWidthPx = 12,
            overflowHintSpacingPx = 4,
            foldedEdgeSafetyPx = 6,
            measureTextWidthPx = { it.length * 10 }
        )
}

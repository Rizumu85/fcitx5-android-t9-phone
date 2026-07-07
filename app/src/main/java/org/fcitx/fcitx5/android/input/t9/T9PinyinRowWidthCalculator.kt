/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinRowWidthCalculator {
    data class Input(
        val items: List<String>,
        val minVisibleChips: Int,
        val chipHorizontalPaddingPx: Int,
        val chipSpacingPx: Int,
        val overflowHintTextWidthPx: Int,
        val overflowHintSpacingPx: Int,
        val foldedEdgeSafetyPx: Int,
        val measureTextWidthPx: (String) -> Int
    )

    data class Widths(
        val fullContentWidthPx: Int,
        val foldedChipContentWidthPx: Int,
        val overflowHintStartPx: Int,
        val foldedContentWidthPx: Int
    )

    fun calculate(input: Input): Widths? {
        if (input.items.isEmpty()) return null
        val full = itemsWidthPx(
            items = input.items,
            input = input
        )
        val foldedItems = input.items.take(input.minVisibleChips.coerceAtLeast(1))
        // Product decision: the ellipsis is part of the folded row's visual rhythm, not an
        // overlay reserve. This keeps the hint close to the fourth chip while still leaving a
        // tiny edge guard for custom-font glyph differences.
        val foldedChips = itemsWidthPx(
            items = foldedItems,
            input = input
        )
        val hintStart = foldedChips + input.overflowHintSpacingPx
        val folded = hintStart + input.overflowHintTextWidthPx + input.foldedEdgeSafetyPx
        return Widths(
            fullContentWidthPx = full,
            foldedChipContentWidthPx = foldedChips,
            overflowHintStartPx = hintStart,
            foldedContentWidthPx = folded
        )
    }

    private fun itemsWidthPx(
        items: List<String>,
        input: Input
    ): Int {
        return items.withIndex().sumOf { (index, item) ->
            val rightMarginPx = if (index != items.lastIndex) {
                input.chipSpacingPx
            } else {
                0
            }
            input.measureTextWidthPx(item) + input.chipHorizontalPaddingPx * 2 + rightMarginPx
        }
    }
}

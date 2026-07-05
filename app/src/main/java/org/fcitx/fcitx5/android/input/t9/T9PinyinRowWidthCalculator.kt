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
        val overflowHintWidthPx: Int,
        val foldedEdgeSafetyPx: Int,
        val measureTextWidthPx: (String) -> Int
    )

    data class Widths(
        val fullContentWidthPx: Int,
        val foldedContentWidthPx: Int
    )

    fun calculate(input: Input): Widths? {
        if (input.items.isEmpty()) return null
        val full = itemsWidthPx(
            items = input.items,
            reservesOverflowHint = false,
            input = input
        )
        val foldedItems = input.items.take(input.minVisibleChips.coerceAtLeast(1))
        // Product decision: the folded pinyin row reserves the quiet ellipsis and a fixed edge
        // guard so the fourth chip stays complete in the short-Hanzi-page overflow case.
        val folded = itemsWidthPx(
            items = foldedItems,
            reservesOverflowHint = true,
            input = input
        )
        return Widths(
            fullContentWidthPx = full,
            foldedContentWidthPx = folded
        )
    }

    private fun itemsWidthPx(
        items: List<String>,
        reservesOverflowHint: Boolean,
        input: Input
    ): Int {
        val chipWidthPx = items.withIndex().sumOf { (index, item) ->
            val rightMarginPx = if (index != items.lastIndex || reservesOverflowHint) {
                input.chipHorizontalPaddingPx
            } else {
                0
            }
            input.measureTextWidthPx(item) + input.chipHorizontalPaddingPx * 2 + rightMarginPx
        }
        val overflowHintWidthPx = if (reservesOverflowHint) input.overflowHintWidthPx else 0
        val foldedEdgeSafetyPx = if (reservesOverflowHint) input.foldedEdgeSafetyPx else 0
        return chipWidthPx + overflowHintWidthPx + foldedEdgeSafetyPx
    }
}

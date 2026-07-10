/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9ReadingRowFoldedPreviewPlanner {
    data class Plan(
        val visibleCount: Int,
        val chipContentWidthPx: Int,
        val overflowHintStartPx: Int
    )

    fun plan(
        itemCount: Int,
        minVisibleCount: Int,
        viewportWidthPx: Int,
        chipWidthsPx: List<Int>,
        chipSpacingPx: Int,
        overflowHintSpacingPx: Int,
        overflowHintEndReservePx: Int
    ): Plan {
        require(itemCount > 0 && chipWidthsPx.size >= itemCount)
        val minimum = minVisibleCount.coerceIn(1, itemCount)
        val availableChipWidth = (viewportWidthPx - overflowHintEndReservePx).coerceAtLeast(1)
        var visibleCount = minimum
        var chipContentWidth = prefixWidthPx(minimum, chipWidthsPx, chipSpacingPx)
        // Product decision: four chips protect the compact edge case, but a wider Hanzi row
        // should expose every additional whole reading chip that its existing bubble can hold.
        while (visibleCount < itemCount) {
            val nextWidth = chipContentWidth + chipSpacingPx + chipWidthsPx[visibleCount]
            if (nextWidth > availableChipWidth) break
            visibleCount += 1
            chipContentWidth = nextWidth
        }
        return Plan(
            visibleCount = visibleCount,
            chipContentWidthPx = chipContentWidth,
            overflowHintStartPx = chipContentWidth + overflowHintSpacingPx
        )
    }

    private fun prefixWidthPx(
        count: Int,
        chipWidthsPx: List<Int>,
        chipSpacingPx: Int
    ): Int = chipWidthsPx.take(count).sum() + chipSpacingPx * (count - 1).coerceAtLeast(0)
}

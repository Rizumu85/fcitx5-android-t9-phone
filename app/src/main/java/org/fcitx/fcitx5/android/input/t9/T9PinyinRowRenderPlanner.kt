/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinRowRenderPlanner {
    data class Plan(
        val displayedItems: List<String>,
        val displayedHighlight: Int,
        val showOverflowHint: Boolean,
        val usesWindowedDisplay: Boolean
    )

    fun plan(
        state: T9PinyinRowWindow.VisibleState,
        rowPlan: T9PinyinOverflowPolicy.Plan?,
        focusedViewportWidthPx: Int? = null,
        focusedEdgeGuardPx: Int = 0,
        chipWidthsPx: List<Int> = emptyList(),
        chipSpacingPx: Int = 0
    ): Plan {
        val focusedFolded = rowPlan?.folded == true && !rowPlan.showHint
        val window = if (focusedFolded &&
            focusedViewportWidthPx != null &&
            focusedViewportWidthPx > 0 &&
            chipWidthsPx.size >= state.items.size
        ) {
            focusedWindow(
                highlightedIndex = state.highlightedIndex,
                itemCount = state.items.size,
                viewportWidthPx = (focusedViewportWidthPx - focusedEdgeGuardPx).coerceAtLeast(1),
                chipWidthsPx = chipWidthsPx,
                chipSpacingPx = chipSpacingPx
            )
        } else {
            0 until (rowPlan?.visibleCount ?: state.items.size).coerceAtMost(state.items.size)
        }
        val displayedItems = if (window.isEmpty()) {
            emptyList()
        } else {
            state.items.slice(window)
        }
        return Plan(
            displayedItems = displayedItems,
            displayedHighlight = (state.highlightedIndex - window.first).coerceIn(
                0,
                displayedItems.lastIndex.coerceAtLeast(0)
            ),
            showOverflowHint = rowPlan?.showHint == true,
            usesWindowedDisplay = !window.isEmpty() && (window.first != 0 || window.last + 1 < state.items.size)
        )
    }

    private fun focusedWindow(
        highlightedIndex: Int,
        itemCount: Int,
        viewportWidthPx: Int,
        chipWidthsPx: List<Int>,
        chipSpacingPx: Int
    ): IntRange {
        if (itemCount <= 0) return 0 until 0
        val highlighted = highlightedIndex.coerceIn(0, itemCount - 1)
        val firstEnd = endFittingFrom(
            start = 0,
            itemCount = itemCount,
            viewportWidthPx = viewportWidthPx,
            chipWidthsPx = chipWidthsPx,
            chipSpacingPx = chipSpacingPx
        )
        if (highlighted <= firstEnd) return 0..firstEnd

        var start = highlighted
        while (start > 0 && widthOf(start - 1, highlighted, chipWidthsPx, chipSpacingPx) <= viewportWidthPx) {
            start -= 1
        }
        val end = endFittingFrom(
            start = start,
            itemCount = itemCount,
            viewportWidthPx = viewportWidthPx,
            chipWidthsPx = chipWidthsPx,
            chipSpacingPx = chipSpacingPx
        )
        return start..end
    }

    private fun endFittingFrom(
        start: Int,
        itemCount: Int,
        viewportWidthPx: Int,
        chipWidthsPx: List<Int>,
        chipSpacingPx: Int
    ): Int {
        var end = start
        while (end + 1 < itemCount &&
            widthOf(start, end + 1, chipWidthsPx, chipSpacingPx) <= viewportWidthPx
        ) {
            end += 1
        }
        return end
    }

    private fun widthOf(
        start: Int,
        end: Int,
        chipWidthsPx: List<Int>,
        chipSpacingPx: Int
    ): Int {
        if (end < start) return 0
        val chips = chipWidthsPx.subList(start, end + 1).sum()
        return chips + chipSpacingPx * (end - start)
    }
}

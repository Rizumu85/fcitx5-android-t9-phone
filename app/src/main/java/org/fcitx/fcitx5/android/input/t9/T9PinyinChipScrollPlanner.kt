/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinChipScrollPlanner {
    data class ItemBounds(val startPx: Int, val endPx: Int)

    data class Plan(
        val scrollX: Int,
        val endPaddingPx: Int
    )

    fun plan(
        currentScrollX: Int,
        viewportWidthPx: Int,
        contentWidthPx: Int,
        itemBounds: List<ItemBounds>,
        highlightedIndex: Int
    ): Plan {
        if (viewportWidthPx <= 0 || itemBounds.isEmpty()) {
            return Plan(scrollX = currentScrollX.coerceAtLeast(0), endPaddingPx = 0)
        }
        val highlighted = highlightedIndex.coerceIn(0, itemBounds.lastIndex)
        val item = itemBounds[highlighted]
        val target = when {
            item.startPx < currentScrollX -> item.startPx
            item.endPx > currentScrollX + viewportWidthPx -> {
                val start = cleanStartForTrailingItem(
                    highlightedIndex = highlighted,
                    viewportWidthPx = viewportWidthPx,
                    itemBounds = itemBounds
                )
                itemBounds[start].startPx
            }
            else -> currentScrollX
        }.coerceAtLeast(0)
        // Product decision: focused folded pinyin should scroll on chip boundaries instead of
        // slicing the leading chip. A small dynamic tail lets the final chip align cleanly even
        // when the content is only slightly wider than the viewport.
        val endPadding = (target + viewportWidthPx - contentWidthPx).coerceAtLeast(0)
        return Plan(scrollX = target, endPaddingPx = endPadding)
    }

    private fun cleanStartForTrailingItem(
        highlightedIndex: Int,
        viewportWidthPx: Int,
        itemBounds: List<ItemBounds>
    ): Int {
        val itemEnd = itemBounds[highlightedIndex].endPx
        for (index in 0..highlightedIndex) {
            if (itemEnd - itemBounds[index].startPx <= viewportWidthPx) {
                return index
            }
        }
        return highlightedIndex
    }
}

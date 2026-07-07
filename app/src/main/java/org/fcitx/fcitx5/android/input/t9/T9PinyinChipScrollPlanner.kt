/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinChipScrollPlanner {
    data class ItemBounds(val startPx: Int, val endPx: Int)

    data class Plan(
        val scrollX: Int
    )

    fun plan(
        currentScrollX: Int,
        viewportWidthPx: Int,
        contentWidthPx: Int,
        itemBounds: List<ItemBounds>,
        highlightedIndex: Int
    ): Plan {
        if (viewportWidthPx <= 0 || itemBounds.isEmpty()) {
            return Plan(scrollX = currentScrollX.coerceAtLeast(0))
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
        }.coerceIn(0, (contentWidthPx - viewportWidthPx).coerceAtLeast(0))
        return Plan(scrollX = target)
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

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
                // The newly revealed chip owns the trailing edge. Snapping to an earlier chip
                // boundary can leave enough spare width to expose the following chip too, making
                // one directional step appear to advance two choices.
                item.endPx - viewportWidthPx
            }
            else -> currentScrollX
        }.coerceIn(0, (contentWidthPx - viewportWidthPx).coerceAtLeast(0))
        return Plan(scrollX = target)
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinChipUpdatePlanner {
    data class Plan(
        val changed: Boolean,
        val bindIndices: List<Int>,
        val styleIndices: List<Int>
    )

    fun plan(
        oldItems: List<String>,
        newItems: List<String>,
        oldHighlightedIndex: Int,
        newHighlightedIndex: Int
    ): Plan {
        if (oldItems == newItems && oldHighlightedIndex == newHighlightedIndex) {
            return Plan(changed = false, bindIndices = emptyList(), styleIndices = emptyList())
        }
        val bind = linkedSetOf<Int>()
        val sharedSize = minOf(oldItems.size, newItems.size)
        for (index in 0 until sharedSize) {
            if (oldItems[index] != newItems[index]) {
                bind += index
            }
        }
        for (index in sharedSize until newItems.size) {
            bind += index
        }
        oldItems.lastIndex.takeIf { it in newItems.indices }?.let { bind += it }
        newItems.lastIndex.takeIf { it in newItems.indices }?.let { bind += it }

        val style = linkedSetOf<Int>()
        style += bind
        oldHighlightedIndex.takeIf { it in newItems.indices }?.let { style += it }
        newHighlightedIndex.takeIf { it in newItems.indices }?.let { style += it }

        return Plan(
            changed = oldItems != newItems,
            bindIndices = bind.toList(),
            styleIndices = style.toList()
        )
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinRowRenderPlanner {
    data class Plan(
        val displayedItems: List<String>,
        val displayedHighlight: Int,
        val showOverflowHint: Boolean
    )

    fun plan(
        state: T9PinyinRowWindow.VisibleState,
        rowPlan: T9PinyinOverflowPolicy.Plan?
    ): Plan {
        val displayedItems = state.items.take(rowPlan?.visibleCount ?: state.items.size)
        return Plan(
            displayedItems = displayedItems,
            displayedHighlight = state.highlightedIndex.coerceIn(
                0,
                displayedItems.lastIndex.coerceAtLeast(0)
            ),
            showOverflowHint = rowPlan?.showHint == true
        )
    }
}

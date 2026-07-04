/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class T9PinyinRowWindow(
    private val maxVisibleItems: Int = DEFAULT_MAX_VISIBLE_ITEMS
) {
    data class VisibleState(
        val items: List<String>,
        val highlightedIndex: Int,
        val windowStart: Int
    )

    private var items: List<String> = emptyList()
    private var highlightedIndex = 0
    private var windowStart = 0

    fun clear() {
        items = emptyList()
        highlightedIndex = 0
        windowStart = 0
    }

    fun submit(nextItems: List<String>): VisibleState {
        if (items != nextItems) {
            items = nextItems.toList()
            highlightedIndex = 0
            windowStart = 0
        }
        return visibleState()
    }

    fun resetHighlight(): VisibleState? {
        if (items.isEmpty()) return null
        if (highlightedIndex == 0 && windowStart == 0) return visibleState()
        highlightedIndex = 0
        windowStart = 0
        return visibleState()
    }

    fun move(delta: Int): VisibleState? {
        if (items.isEmpty()) return null
        val nextIndex = (highlightedIndex + delta).coerceIn(0, items.lastIndex)
        if (nextIndex == highlightedIndex) return null
        highlightedIndex = nextIndex
        windowStart = windowStartFor(highlightedIndex)
        return visibleState()
    }

    fun highlightedPinyin(): String? = items.getOrNull(highlightedIndex)

    private fun visibleState(): VisibleState {
        if (items.isEmpty()) {
            return VisibleState(emptyList(), 0, 0)
        }
        val start = windowStart.coerceIn(0, maxWindowStart())
        val end = (start + maxVisibleItems).coerceAtMost(items.size)
        return VisibleState(
            items = items.subList(start, end),
            highlightedIndex = (highlightedIndex - start).coerceIn(0, (end - start - 1).coerceAtLeast(0)),
            windowStart = start
        )
    }

    private fun windowStartFor(index: Int): Int {
        if (items.size <= maxVisibleItems) return 0
        val currentEnd = windowStart + maxVisibleItems
        return when {
            index < windowStart -> index
            index >= currentEnd -> index - maxVisibleItems + 1
            else -> windowStart.coerceIn(0, maxWindowStart())
        }
    }

    private fun maxWindowStart(): Int =
        (items.size - maxVisibleItems).coerceAtLeast(0)

    companion object {
        const val DEFAULT_MAX_VISIBLE_ITEMS = 8
    }
}

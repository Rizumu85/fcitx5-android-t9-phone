/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinChipStripLayout {
    data class Input(
        val items: List<String>,
        val chipHorizontalPaddingPx: Int,
        val chipSpacingPx: Int,
        val measureTextWidthPx: (String) -> Int
    )

    data class ItemBounds(
        val startPx: Int,
        val endPx: Int,
        val visualEndPx: Int
    )

    data class Frame(
        val items: List<String>,
        val itemBounds: List<ItemBounds>,
        val contentWidthPx: Int
    ) {
        companion object {
            val Empty = Frame(emptyList(), emptyList(), 0)
        }
    }

    fun plan(input: Input): Frame {
        if (input.items.isEmpty()) return Frame.Empty
        val padding = input.chipHorizontalPaddingPx.coerceAtLeast(0)
        val spacing = input.chipSpacingPx.coerceAtLeast(0)
        val bounds = mutableListOf<ItemBounds>()
        var x = 0
        input.items.forEachIndexed { index, item ->
            val textWidth = input.measureTextWidthPx(item).coerceAtLeast(0)
            val chipWidth = textWidth + padding * 2
            val visualEnd = x + chipWidth
            val margin = if (index != input.items.lastIndex) spacing else 0
            bounds += ItemBounds(
                startPx = x,
                endPx = visualEnd + margin,
                visualEndPx = visualEnd
            )
            x += chipWidth + margin
        }
        return Frame(
            items = input.items.toList(),
            itemBounds = bounds,
            contentWidthPx = x
        )
    }
}

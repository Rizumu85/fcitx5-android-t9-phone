/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9PinyinChipStripLayoutTest {
    @Test
    fun computesContentWidthAndVisualChipBoundsFromOneFrame() {
        val frame = T9PinyinChipStripLayout.plan(
            T9PinyinChipStripLayout.Input(
                items = listOf("ge", "he", "g"),
                chipHorizontalPaddingPx = 3,
                chipSpacingPx = 4,
                measureTextWidthPx = { it.length * 10 }
            )
        )

        assertEquals(76, frame.contentWidthPx)
        assertEquals(T9PinyinChipStripLayout.ItemBounds(0, 30, 26), frame.itemBounds[0])
        assertEquals(T9PinyinChipStripLayout.ItemBounds(30, 60, 56), frame.itemBounds[1])
        assertEquals(T9PinyinChipStripLayout.ItemBounds(60, 76, 76), frame.itemBounds[2])
    }

    @Test
    fun emptyFrameHasNoWidth() {
        val frame = T9PinyinChipStripLayout.plan(
            T9PinyinChipStripLayout.Input(
                items = emptyList(),
                chipHorizontalPaddingPx = 3,
                chipSpacingPx = 4,
                measureTextWidthPx = { it.length * 10 }
            )
        )

        assertEquals(0, frame.contentWidthPx)
        assertEquals(emptyList<String>(), frame.items)
    }
}

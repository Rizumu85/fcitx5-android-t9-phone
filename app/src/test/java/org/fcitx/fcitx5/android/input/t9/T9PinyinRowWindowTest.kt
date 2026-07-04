/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class T9PinyinRowWindowTest {

    @Test
    fun submitShowsOnlyVisibleWindow() {
        val window = T9PinyinRowWindow(maxVisibleItems = 3)

        val state = window.submit(listOf("a", "b", "c", "d", "e"))

        assertEquals(listOf("a", "b", "c"), state.items)
        assertEquals(0, state.highlightedIndex)
        assertEquals(0, state.windowStart)
        assertEquals("a", window.highlightedPinyin())
    }

    @Test
    fun movingPastWindowEndSlidesVisibleWindow() {
        val window = T9PinyinRowWindow(maxVisibleItems = 3)
        window.submit(listOf("a", "b", "c", "d", "e"))

        window.move(1)
        window.move(1)
        val state = window.move(1)

        assertEquals(listOf("b", "c", "d"), state?.items)
        assertEquals(2, state?.highlightedIndex)
        assertEquals(1, state?.windowStart)
        assertEquals("d", window.highlightedPinyin())
    }

    @Test
    fun changedListResetsHighlightToFirstOption() {
        val window = T9PinyinRowWindow(maxVisibleItems = 3)
        window.submit(listOf("a", "b", "c"))
        window.move(1)

        val state = window.submit(listOf("x", "b", "y", "z"))

        assertEquals(listOf("x", "b", "y"), state.items)
        assertEquals(0, state.highlightedIndex)
        assertEquals("x", window.highlightedPinyin())
    }

    @Test
    fun resetHighlightReturnsToFirstWindow() {
        val window = T9PinyinRowWindow(maxVisibleItems = 3)
        window.submit(listOf("a", "b", "c", "d", "e"))
        window.move(3)

        val state = window.resetHighlight()

        assertEquals(listOf("a", "b", "c"), state?.items)
        assertEquals(0, state?.highlightedIndex)
        assertEquals(0, state?.windowStart)
        assertEquals("a", window.highlightedPinyin())
    }

    @Test
    fun clearDropsHighlightedPinyin() {
        val window = T9PinyinRowWindow(maxVisibleItems = 3)
        window.submit(listOf("a", "b"))

        window.clear()

        assertNull(window.highlightedPinyin())
    }
}

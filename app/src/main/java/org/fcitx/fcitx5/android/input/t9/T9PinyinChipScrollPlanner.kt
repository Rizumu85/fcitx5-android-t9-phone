/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinChipScrollPlanner {
    fun targetScrollX(
        currentScrollX: Int,
        viewportWidthPx: Int,
        itemStartPx: Int,
        itemEndPx: Int
    ): Int {
        if (viewportWidthPx <= 0) return currentScrollX.coerceAtLeast(0)
        // Product decision: focus movement should reveal the current chip just enough; pulling
        // the next hidden pinyin into view makes the folded row feel like it jumped two steps.
        return when {
            itemStartPx < currentScrollX -> itemStartPx
            itemEndPx > currentScrollX + viewportWidthPx -> itemEndPx - viewportWidthPx
            else -> currentScrollX
        }.coerceAtLeast(0)
    }
}

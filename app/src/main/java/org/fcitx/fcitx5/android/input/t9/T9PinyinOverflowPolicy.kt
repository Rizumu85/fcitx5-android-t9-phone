/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinOverflowPolicy {
    data class Plan(
        val folded: Boolean,
        val showHint: Boolean,
        val visibleCount: Int
    )

    fun plan(
        pinyinCount: Int,
        fullContentWidthPx: Int,
        candidateRowWidthPx: Int,
        maxRowWidthPx: Int,
        minVisibleChips: Int,
        pinyinRowFocused: Boolean
    ): Plan {
        val folded = shouldFold(
            pinyinCount = pinyinCount,
            fullContentWidthPx = fullContentWidthPx,
            candidateRowWidthPx = candidateRowWidthPx,
            maxRowWidthPx = maxRowWidthPx,
            minVisibleChips = minVisibleChips
        )
        val showHint = folded && !pinyinRowFocused
        return Plan(
            folded = folded,
            showHint = showHint,
            visibleCount = if (showHint) minVisibleChips.coerceAtMost(pinyinCount) else pinyinCount
        )
    }

    fun shouldFold(
        pinyinCount: Int,
        fullContentWidthPx: Int,
        candidateRowWidthPx: Int,
        maxRowWidthPx: Int,
        minVisibleChips: Int
    ): Boolean {
        if (pinyinCount <= minVisibleChips) return false
        val availableWidth = candidateRowWidthPx
            .coerceAtLeast(1)
            .coerceAtMost(maxRowWidthPx.coerceAtLeast(1))
        // Product decision: the ellipsis is only for the short-Hanzi-page edge case. Normal
        // candidate rows should keep all pinyin chips visible so the bubble still feels elastic.
        return fullContentWidthPx > availableWidth
    }
}

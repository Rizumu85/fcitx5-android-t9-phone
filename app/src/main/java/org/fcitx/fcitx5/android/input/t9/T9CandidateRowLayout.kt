/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9CandidateRowLayout {
    fun paginationVisible(hasPrev: Boolean, hasNext: Boolean): Boolean =
        hasPrev || hasNext

    fun rowWidth(
        itemWidths: List<Int>,
        itemSpacingPx: Int,
        paginationWidthPx: Int,
        hasPagination: Boolean,
        sidePaddingPx: Int = 0
    ): Int {
        if (itemWidths.isEmpty()) return 0
        val candidateSpacingCount = (itemWidths.size - 1).coerceAtLeast(0)
        val paginationSpacingCount = if (hasPagination && paginationWidthPx > 0) 1 else 0
        val spacingWidth = (candidateSpacingCount + paginationSpacingCount) * itemSpacingPx
        val paginationWidth = if (hasPagination) paginationWidthPx else 0
        return sidePaddingPx + itemWidths.sum() + spacingWidth + paginationWidth
    }
}

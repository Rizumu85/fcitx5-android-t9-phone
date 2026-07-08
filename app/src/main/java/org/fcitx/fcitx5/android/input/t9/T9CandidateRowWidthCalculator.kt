/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

object T9CandidateRowWidthCalculator {
    data class Input(
        val data: FcitxEvent.PagedCandidateEvent.Data,
        val widthBudget: T9CandidateWidthBudget,
        val rowHorizontalPaddingPx: Int,
        val trailingPaddingPx: Int,
        val showPaginationArrows: Boolean,
        val paginationWidthPx: Int
    )

    fun calculate(input: Input): Int? {
        if (input.data.candidates.isEmpty()) return null
        val spacing = input.widthBudget.candidateSpacingPx.coerceAtLeast(0)
        val hasPagination = input.showPaginationArrows && (input.data.hasPrev || input.data.hasNext)
        val inactiveCandidateWidth = input.data.candidates.sumOf { candidate ->
            input.widthBudget.candidateChipWidthPx(
                candidate = candidate,
                enforceMinimumWidth = true
            )
        } + spacing * (input.data.candidates.size - 1).coerceAtLeast(0)
        val paginationWidth = if (input.showPaginationArrows && (input.data.hasPrev || input.data.hasNext)) {
            spacing + input.paginationWidthPx.coerceAtLeast(0)
        } else {
            0
        }
        val tailScaleOverflow = 0
        // Product decision: the last shortcut chip keeps the same minimum visual width as the
        // others. Its text aligns to the bubble tail in the Android adapter, so the row stays
        // stable without making the final focused chip look smaller.
        return T9ShortcutTailPolicy.plannedToolbarWidthPx(
            candidateContentWidthPx = inactiveCandidateWidth + paginationWidth,
            tailScaleOverflowPx = tailScaleOverflow,
            edgePaddingPx = input.rowHorizontalPaddingPx,
            trailingPaddingPx = input.trailingPaddingPx,
            maxRowWidthPx = input.widthBudget.maxWidthPx
        )
            .coerceAtLeast(1)
    }
}

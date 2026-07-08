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
        val lastCandidateIndex = input.data.candidates.lastIndex
        val inactiveCandidateWidth = input.data.candidates.mapIndexed { index, candidate ->
            val edgeAlignedTail = !hasPagination && index == lastCandidateIndex
            input.widthBudget.candidateChipWidthPx(
                candidate = candidate,
                enforceMinimumWidth = !edgeAlignedTail
            )
        }.sum() + spacing * (input.data.candidates.size - 1).coerceAtLeast(0)
        val paginationWidth = if (input.showPaginationArrows && (input.data.hasPrev || input.data.hasNext)) {
            spacing + input.paginationWidthPx.coerceAtLeast(0)
        } else {
            0
        }
        val tailScaleOverflow = if (
            !hasPagination &&
            input.data.cursorIndex == lastCandidateIndex
        ) {
            input.widthBudget.activeScaleOverflowPx(
                candidate = input.data.candidates[lastCandidateIndex],
                enforceMinimumWidth = false
            )
        } else {
            0
        }
        // Product decision: inter-candidate spacing and the final breathing room are separate.
        // The tail reserve is fixed so the last candidate never inherits noise from text-width
        // estimates, while pagination still uses a conservative row-width budget.
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

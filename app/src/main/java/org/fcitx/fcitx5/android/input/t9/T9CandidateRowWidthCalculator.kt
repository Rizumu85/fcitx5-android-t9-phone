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
        val showPaginationArrows: Boolean,
        val paginationWidthPx: Int
    )

    fun calculate(input: Input): Int? {
        if (input.data.candidates.isEmpty()) return null
        val inactiveCandidateWidth = input.data.candidates.sumOf { candidate ->
            input.widthBudget.candidateWidthPx(candidate, active = false)
        } - input.widthBudget.candidateSpacingPx.coerceAtLeast(0)
        val paginationWidth = if (input.showPaginationArrows && (input.data.hasPrev || input.data.hasNext)) {
            input.paginationWidthPx.coerceAtLeast(0)
        } else {
            0
        }
        // Product decision: the T9 candidate bubble follows the current page content, not
        // RecyclerView's previous measured width. Short Hanzi pages should not inherit a wide
        // page. Focus is drawn with View scale, which does not participate in natural layout
        // measurement; the RecyclerView padding provides the drawing inset instead.
        return (inactiveCandidateWidth + paginationWidth + input.rowHorizontalPaddingPx * 2)
            .coerceAtMost(input.widthBudget.maxWidthPx)
            .coerceAtLeast(1)
    }
}

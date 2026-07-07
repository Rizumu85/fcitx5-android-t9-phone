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
        val paginationWidthPx: Int
    )

    fun calculate(input: Input): Int? {
        if (input.data.candidates.isEmpty()) return null
        val candidateWidth = input.data.candidates.sumOf { candidate ->
            input.widthBudget.candidateWidthPx(candidate)
        }
        val paginationWidth = if (input.data.hasPrev || input.data.hasNext) {
            input.paginationWidthPx.coerceAtLeast(0)
        } else {
            0
        }
        // Product decision: the T9 candidate bubble follows the current page content, not
        // RecyclerView's previous measured width. Short Hanzi pages should not inherit a wide
        // page, while the rendered toolbar still keeps its focus-overflow safety inset.
        return (candidateWidth + paginationWidth + input.rowHorizontalPaddingPx * 2)
            .coerceAtMost(input.widthBudget.maxWidthPx)
            .coerceAtLeast(1)
    }
}

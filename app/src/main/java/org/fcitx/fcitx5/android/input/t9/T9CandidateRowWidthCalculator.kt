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
        val hasPagination = input.showPaginationArrows && (input.data.hasPrev || input.data.hasNext)
        val candidateWidths = input.data.candidates.map(input.widthBudget::naturalCandidateWidthPx)
        val focusMargins = T9CandidateFocusEnvelope.candidateEndMarginsPx(
            candidateWidthsPx = candidateWidths,
            itemSpacingPx = input.widthBudget.candidateSpacingPx,
            hasTrailingItem = hasPagination,
            scalePercent = input.widthBudget.activeScalePercent
        )
        val paginationWidth = if (hasPagination) input.paginationWidthPx.coerceAtLeast(0) else 0
        // Focus scaling is a drawing transform, so the policy width must explicitly reserve the
        // same stable envelope as the Android row. Otherwise a long leading candidate can occupy
        // the next chip even though paging itself used a conservative width budget.
        return (
            candidateWidths.sum() +
                focusMargins.sum() +
                paginationWidth +
                input.rowHorizontalPaddingPx * 2 +
                input.trailingPaddingPx.coerceAtLeast(0)
            )
            .coerceAtMost(input.widthBudget.maxWidthPx)
            .coerceAtLeast(1)
    }
}

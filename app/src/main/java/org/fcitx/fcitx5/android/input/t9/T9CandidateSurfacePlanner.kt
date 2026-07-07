/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

object T9CandidateSurfacePlanner {
    data class Input(
        val candidates: FcitxEvent.PagedCandidateEvent.Data,
        val widthBudget: T9CandidateWidthBudget,
        val rowHorizontalPaddingPx: Int,
        val trailingPaddingPx: Int,
        val showPaginationArrows: Boolean,
        val paginationWidthPx: Int,
        val candidateVisualWidthPx: Int?,
        val pinyinState: T9PinyinRowWindow.VisibleState?,
        val pinyinWidths: T9PinyinRowWidthCalculator.Widths?,
        val pinyinChipWidthsPx: List<Int>,
        val pinyinChipSpacingPx: Int,
        val pinyinFallbackViewportWidthPx: Int?,
        val maxRowWidthPx: Int,
        val minVisiblePinyinChips: Int,
        val pinyinRowFocused: Boolean
    )

    data class Plan(
        val shortcutLayout: T9ShortcutCandidateLayout,
        val candidatePolicyWidthPx: Int?,
        val pinyinSurface: T9PinyinRowSurfacePlanner.Plan?
    )

    fun plan(input: Input): Plan {
        val candidatePolicyWidth = T9CandidateRowWidthCalculator.calculate(
            T9CandidateRowWidthCalculator.Input(
                data = input.candidates,
                widthBudget = input.widthBudget,
                rowHorizontalPaddingPx = input.rowHorizontalPaddingPx,
                trailingPaddingPx = input.trailingPaddingPx,
                showPaginationArrows = input.showPaginationArrows,
                paginationWidthPx = input.paginationWidthPx
            )
        )
        val shortcutLayout = T9ShortcutCandidateLayout(
            maxCandidateWidthPx = input.widthBudget.maxCandidateWidthPx,
            // Decision: render-time T9 candidate rows wrap their actual chip content. This
            // layout width remains zero until a future visual-width model intentionally owns it.
            rowWidthPx = 0,
            edgePaddingPx = input.rowHorizontalPaddingPx,
            maxRowWidthPx = input.widthBudget.maxWidthPx,
            trailingPaddingPx = input.trailingPaddingPx
        )
        val pinyinSurface = pinyinSurface(input, input.candidateVisualWidthPx ?: candidatePolicyWidth)
        return Plan(
            shortcutLayout = shortcutLayout,
            candidatePolicyWidthPx = candidatePolicyWidth,
            pinyinSurface = pinyinSurface
        )
    }

    private fun pinyinSurface(
        input: Input,
        candidateSurfaceWidth: Int?
    ): T9PinyinRowSurfacePlanner.Plan? {
        return T9PinyinRowSurfacePlanner.plan(
            T9PinyinRowSurfacePlanner.Input(
                candidateMeasuredWidthPx = candidateSurfaceWidth,
                fallbackViewportWidthPx = input.pinyinFallbackViewportWidthPx,
                state = input.pinyinState,
                widths = input.pinyinWidths,
                chipWidthsPx = input.pinyinChipWidthsPx,
                chipSpacingPx = input.pinyinChipSpacingPx,
                maxRowWidthPx = input.maxRowWidthPx,
                minVisibleChips = input.minVisiblePinyinChips,
                focused = input.pinyinRowFocused
            )
        )
    }
}

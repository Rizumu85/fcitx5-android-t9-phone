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
        val pinyinViewportWidthPx: Int?,
        val maxRowWidthPx: Int,
        val minVisiblePinyinChips: Int,
        val pinyinRowFocused: Boolean
    )

    data class Plan(
        val shortcutLayout: T9ShortcutCandidateLayout,
        val candidatePolicyWidthPx: Int?,
        val surfaceLayout: T9CandidateSurfaceLayout.Plan?,
        val pinyinVisual: T9PinyinRowVisualPlanner.Plan?
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
            trailingPaddingPx = input.trailingPaddingPx
        )
        val surfaceLayout = surfaceLayout(input, input.candidateVisualWidthPx ?: candidatePolicyWidth)
        val pinyinVisual = pinyinVisual(input, surfaceLayout)
        return Plan(
            shortcutLayout = shortcutLayout,
            candidatePolicyWidthPx = candidatePolicyWidth,
            surfaceLayout = surfaceLayout,
            pinyinVisual = pinyinVisual
        )
    }

    private fun surfaceLayout(
        input: Input,
        candidateSurfaceWidth: Int?
    ): T9CandidateSurfaceLayout.Plan? {
        candidateSurfaceWidth ?: return null
        val pinyinWidths = input.pinyinWidths ?: return null
        val pinyinState = input.pinyinState ?: return null
        if (pinyinState.items.isEmpty()) return null
        return T9CandidateSurfaceLayout.plan(
            T9CandidateSurfaceLayout.Input(
                candidateMeasuredWidthPx = candidateSurfaceWidth,
                pinyinCount = pinyinState.items.size,
                pinyinFullContentWidthPx = pinyinWidths.fullContentWidthPx,
                pinyinFoldedContentWidthPx = pinyinWidths.foldedContentWidthPx,
                maxRowWidthPx = input.maxRowWidthPx,
                minVisiblePinyinChips = input.minVisiblePinyinChips,
                pinyinRowFocused = input.pinyinRowFocused
            )
        )
    }

    private fun pinyinVisual(
        input: Input,
        surfaceLayout: T9CandidateSurfaceLayout.Plan?
    ): T9PinyinRowVisualPlanner.Plan? {
        val pinyinState = input.pinyinState ?: return null
        val pinyinWidths = input.pinyinWidths ?: return null
        val rowWidth = surfaceLayout?.rowWidthPx
            ?: input.pinyinViewportWidthPx
            ?: pinyinWidths.fullContentWidthPx
        return T9PinyinRowVisualPlanner.plan(
            T9PinyinRowVisualPlanner.Input(
                state = pinyinState,
                rowPlan = surfaceLayout?.pinyin,
                rowWidthPx = rowWidth,
                widths = pinyinWidths,
                chipWidthsPx = input.pinyinChipWidthsPx,
                chipSpacingPx = input.pinyinChipSpacingPx
            )
        )
    }
}

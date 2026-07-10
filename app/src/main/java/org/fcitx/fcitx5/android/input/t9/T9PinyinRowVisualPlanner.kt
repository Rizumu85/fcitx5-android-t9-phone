/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinRowVisualPlanner {
    data class Input(
        val state: T9PinyinRowWindow.VisibleState,
        val rowPlan: T9PinyinOverflowPolicy.Plan?,
        val rowWidthPx: Int,
        val widths: T9PinyinRowWidthCalculator.Widths,
        val chipWidthsPx: List<Int>,
        val chipSpacingPx: Int
    )

    data class Plan(
        val displayedItems: List<String>,
        val displayedHighlight: Int,
        val showOverflowHint: Boolean,
        val overflowHintStartPx: Int,
        val pinyinBarWidthPx: Int,
        val usesWindowedDisplay: Boolean
    )

    fun plan(input: Input): Plan {
        val foldedPreview = input.rowPlan
            ?.takeIf { it.showHint }
            ?.let { rowPlan ->
                T9ReadingRowFoldedPreviewPlanner.plan(
                    itemCount = input.state.items.size,
                    minVisibleCount = rowPlan.visibleCount,
                    viewportWidthPx = input.rowWidthPx,
                    chipWidthsPx = input.chipWidthsPx,
                    chipSpacingPx = input.chipSpacingPx,
                    overflowHintSpacingPx = input.widths.overflowHintStartPx -
                        input.widths.foldedChipContentWidthPx,
                    overflowHintEndReservePx = input.widths.foldedContentWidthPx -
                        input.widths.foldedChipContentWidthPx
                )
            }
        val effectiveRowPlan = if (foldedPreview != null) {
            input.rowPlan.copy(visibleCount = foldedPreview.visibleCount)
        } else {
            input.rowPlan
        }
        val renderPlan = T9PinyinRowRenderPlanner.plan(
            state = input.state,
            rowPlan = effectiveRowPlan,
            focusedViewportWidthPx = input.rowWidthPx,
            focusedEdgeGuardPx = 0,
            chipWidthsPx = input.chipWidthsPx,
            chipSpacingPx = input.chipSpacingPx
        )
        return Plan(
            displayedItems = renderPlan.displayedItems,
            displayedHighlight = renderPlan.displayedHighlight,
            showOverflowHint = renderPlan.showOverflowHint,
            overflowHintStartPx = if (renderPlan.showOverflowHint) {
                requireNotNull(foldedPreview).overflowHintStartPx
            } else {
                0
            },
            pinyinBarWidthPx = if (renderPlan.showOverflowHint) {
                requireNotNull(foldedPreview).chipContentWidthPx
            } else {
                input.rowWidthPx
            },
            usesWindowedDisplay = renderPlan.usesWindowedDisplay
        )
    }

}

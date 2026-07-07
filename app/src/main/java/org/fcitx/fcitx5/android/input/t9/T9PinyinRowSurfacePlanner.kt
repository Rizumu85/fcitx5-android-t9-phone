/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinRowSurfacePlanner {
    data class Input(
        val candidateMeasuredWidthPx: Int?,
        val fallbackViewportWidthPx: Int?,
        val state: T9PinyinRowWindow.VisibleState?,
        val widths: T9PinyinRowWidthCalculator.Widths?,
        val chipWidthsPx: List<Int>,
        val chipSpacingPx: Int,
        val maxRowWidthPx: Int,
        val minVisibleChips: Int,
        val focused: Boolean
    )

    data class Plan(
        val contentReady: Boolean,
        val rowWidthPx: Int?,
        val displayedItems: List<String>,
        val displayedHighlight: Int,
        val showOverflowHint: Boolean,
        val overflowHintStartPx: Int,
        val pinyinBarWidthPx: Int,
        val usesWindowedDisplay: Boolean
    )

    fun plan(input: Input): Plan? {
        // Decision: keep the folded pinyin bubble's visual contract behind one seam so
        // short Hanzi pages cannot reintroduce width, ellipsis, and focus-window drift.
        val state = input.state ?: return null
        if (state.items.isEmpty()) return null
        val widths = input.widths ?: return null
        val layout = input.candidateMeasuredWidthPx?.let { candidateWidth ->
            T9CandidateSurfaceLayout.plan(
                T9CandidateSurfaceLayout.Input(
                    candidateMeasuredWidthPx = candidateWidth,
                    pinyinCount = state.items.size,
                    pinyinFullContentWidthPx = widths.fullContentWidthPx,
                    pinyinFoldedContentWidthPx = widths.foldedContentWidthPx,
                    maxRowWidthPx = input.maxRowWidthPx,
                    minVisiblePinyinChips = input.minVisibleChips,
                    pinyinRowFocused = input.focused
                )
            )
        }
        val rowWidth = layout?.rowWidthPx
            ?: input.fallbackViewportWidthPx
            ?: widths.fullContentWidthPx
        val visual = T9PinyinRowVisualPlanner.plan(
            T9PinyinRowVisualPlanner.Input(
                state = state,
                rowPlan = layout?.pinyin,
                rowWidthPx = rowWidth,
                widths = widths,
                chipWidthsPx = input.chipWidthsPx,
                chipSpacingPx = input.chipSpacingPx
            )
        )
        return Plan(
            contentReady = layout?.contentReady == true,
            rowWidthPx = layout?.rowWidthPx,
            displayedItems = visual.displayedItems,
            displayedHighlight = visual.displayedHighlight,
            showOverflowHint = visual.showOverflowHint,
            overflowHintStartPx = visual.overflowHintStartPx,
            pinyinBarWidthPx = visual.pinyinBarWidthPx,
            usesWindowedDisplay = visual.usesWindowedDisplay
        )
    }
}

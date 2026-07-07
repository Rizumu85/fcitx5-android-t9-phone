/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateSurfacePlannerTest {
    @Test
    fun plansShortcutTailCandidatePolicyAndFoldedPinyinTogether() {
        val plan = T9CandidateSurfacePlanner.plan(
            input(
                candidates = listOf("好", "的"),
                trailingPaddingPx = 7
            )
        )

        assertEquals(7, plan.shortcutLayout.trailingPaddingPx)
        assertEquals(5, plan.shortcutLayout.edgePaddingPx)
        assertEquals(0, plan.shortcutLayout.rowWidthPx)
        assertEquals(296, plan.shortcutLayout.maxCandidateWidthPx)
        assertEquals(300, plan.shortcutLayout.maxRowWidthPx)
        assertEquals(73, plan.candidatePolicyWidthPx)
        assertEquals(150, plan.surfaceLayout?.rowWidthPx)
        assertTrue(requireNotNull(plan.surfaceLayout?.pinyin).folded)
        assertTrue(requireNotNull(plan.surfaceLayout?.pinyin).showHint)
        assertEquals(128, requireNotNull(plan.pinyinVisual).pinyinBarWidthPx)
        assertEquals(132, requireNotNull(plan.pinyinVisual).overflowHintStartPx)
    }

    @Test
    fun keepsPinyinVisualAvailableBeforeCandidateSurfaceIsReady() {
        val plan = T9CandidateSurfacePlanner.plan(
            input(
                candidates = emptyList(),
                pinyinViewportWidthPx = 150
            )
        )

        assertEquals(null, plan.candidatePolicyWidthPx)
        assertEquals(null, plan.surfaceLayout)
        assertNotNull(plan.pinyinVisual)
        assertEquals(150, requireNotNull(plan.pinyinVisual).pinyinBarWidthPx)
    }

    @Test
    fun usesRenderedCandidateWidthForPinyinSurfaceWhenAvailable() {
        val plan = T9CandidateSurfacePlanner.plan(
            input(
                candidates = listOf("嘿", "给", "黑", "𬭶", "hehe", "heil", "heir"),
                candidateVisualWidthPx = 478,
                maxRowWidthPx = 600
            )
        )

        assertEquals(478, plan.surfaceLayout?.rowWidthPx)
        assertEquals(478, requireNotNull(plan.pinyinVisual).pinyinBarWidthPx)
    }

    private fun input(
        candidates: List<String>,
        trailingPaddingPx: Int = 0,
        pinyinViewportWidthPx: Int? = null,
        candidateVisualWidthPx: Int? = null,
        maxRowWidthPx: Int = 320
    ): T9CandidateSurfacePlanner.Input =
        T9CandidateSurfacePlanner.Input(
            candidates = FcitxEvent.PagedCandidateEvent.Data(
                candidates = candidates.map {
                    FcitxEvent.Candidate(label = "", text = it, comment = "")
                }.toTypedArray(),
                cursorIndex = 0,
                layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
                hasPrev = false,
                hasNext = false
            ),
            widthBudget = T9CandidateWidthBudget(
                maxWidthPx = 300,
                candidateSpacingPx = 4,
                candidateHorizontalPaddingPx = 3,
                minimumCandidateWidthPx = 10,
                activeScalePercent = 100,
                measureTextWidthPx = { it.length * 20 }
            ),
            rowHorizontalPaddingPx = 5,
            trailingPaddingPx = trailingPaddingPx,
            showPaginationArrows = true,
            paginationWidthPx = 20,
            candidateVisualWidthPx = candidateVisualWidthPx,
            pinyinState = T9PinyinRowWindow.VisibleState(
                items = listOf("gei", "hei", "ge", "he", "g", "h", "i"),
                highlightedIndex = 0,
                windowStart = 0
            ),
            pinyinWidths = T9PinyinRowWidthCalculator.Widths(
                fullContentWidthPx = 260,
                foldedChipContentWidthPx = 128,
                overflowHintStartPx = 132,
                foldedContentWidthPx = 150
            ),
            pinyinChipWidthsPx = listOf(34, 34, 24, 24, 14, 14, 14),
            pinyinChipSpacingPx = 4,
            pinyinViewportWidthPx = pinyinViewportWidthPx,
            maxRowWidthPx = maxRowWidthPx,
            minVisiblePinyinChips = 4,
            pinyinRowFocused = false
        )
}

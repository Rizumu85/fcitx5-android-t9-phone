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
        assertEquals(73, plan.shortcutLayout.rowWidthPx)
        assertEquals(296, plan.shortcutLayout.maxCandidateWidthPx)
        assertEquals(300, plan.shortcutLayout.maxRowWidthPx)
        assertEquals(73, plan.candidatePolicyWidthPx)
        val pinyinSurface = requireNotNull(plan.pinyinSurface)
        assertEquals(150, pinyinSurface.rowWidthPx)
        assertTrue(pinyinSurface.contentReady)
        assertTrue(pinyinSurface.showOverflowHint)
        assertEquals(128, pinyinSurface.pinyinBarWidthPx)
        assertEquals(132, pinyinSurface.overflowHintStartPx)
    }

    @Test
    fun keepsPinyinVisualAvailableBeforeCandidateSurfaceIsReady() {
        val plan = T9CandidateSurfacePlanner.plan(
            input(
                candidates = emptyList(),
                pinyinFallbackViewportWidthPx = 150
            )
        )

        assertEquals(null, plan.candidatePolicyWidthPx)
        assertNotNull(plan.pinyinSurface)
        assertEquals(false, requireNotNull(plan.pinyinSurface).contentReady)
        assertEquals(null, requireNotNull(plan.pinyinSurface).rowWidthPx)
        assertEquals(150, requireNotNull(plan.pinyinSurface).pinyinBarWidthPx)
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

        assertEquals(478, plan.pinyinSurface?.rowWidthPx)
        assertEquals(478, requireNotNull(plan.pinyinSurface).pinyinBarWidthPx)
    }

    @Test
    fun shortcutLayoutWidthFollowsContentWithoutMinimumWidthTailNoise() {
        val plan = T9CandidateSurfacePlanner.plan(
            input(
                candidates = listOf("I"),
                trailingPaddingPx = 7
            )
        )

        assertEquals(43, plan.shortcutLayout.rowWidthPx)
        assertEquals(43, plan.candidatePolicyWidthPx)
    }

    private fun input(
        candidates: List<String>,
        trailingPaddingPx: Int = 0,
        pinyinFallbackViewportWidthPx: Int? = null,
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
            pinyinFallbackViewportWidthPx = pinyinFallbackViewportWidthPx,
            maxRowWidthPx = maxRowWidthPx,
            minVisiblePinyinChips = 4,
            pinyinRowFocused = false
        )
}

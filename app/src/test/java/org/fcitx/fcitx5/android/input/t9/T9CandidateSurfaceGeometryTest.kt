/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateSurfaceGeometryTest {
    private val geometry = T9CandidateSurfaceGeometry(
        measurePinyinTextWidthPx = { it.length * 10 },
        measureCandidateTextWidthPx = { it.length * 20 }
    )

    @Test
    fun buildsSurfacePlanFromOneGeometryInput() {
        val plan = geometry.surfacePlan(
            surfaceInput(
                candidates = listOf("好", "的"),
                candidateVisualWidthPx = null
            )
        )

        assertEquals(7, plan.shortcutLayout.trailingPaddingPx)
        assertEquals(5, plan.shortcutLayout.edgePaddingPx)
        assertEquals(296, plan.shortcutLayout.maxCandidateWidthPx)
        assertEquals(73, plan.candidatePolicyWidthPx)
        val pinyinSurface = requireNotNull(plan.pinyinSurface)
        assertTrue(pinyinSurface.showOverflowHint)
        assertEquals(144, pinyinSurface.rowWidthPx)
        assertEquals(128, pinyinSurface.pinyinBarWidthPx)
    }

    @Test
    fun keepsPinyinSurfaceAvailableBeforeCandidateVisualWidthIsMeasured() {
        val plan = geometry.pinyinSurfacePlan(
            input = surfaceInput(
                candidates = emptyList(),
                candidateVisualWidthPx = null,
                fallbackViewportWidthPx = 140
            ),
            candidateRowWidthPx = null
        )

        val pinyinSurface = requireNotNull(plan)
        assertFalse(pinyinSurface.contentReady)
        assertEquals(null, pinyinSurface.rowWidthPx)
        assertEquals(140, pinyinSurface.pinyinBarWidthPx)
    }

    @Test
    fun overrideCandidateWidthOnlyAffectsThePinyinSurfacePass() {
        val plan = geometry.pinyinSurfacePlan(
            input = surfaceInput(
                candidates = listOf("嘿", "给"),
                candidateVisualWidthPx = null
            ),
            candidateRowWidthPx = 212
        )

        val pinyinSurface = requireNotNull(plan)
        assertTrue(pinyinSurface.contentReady)
        assertEquals(212, pinyinSurface.rowWidthPx)
        assertEquals(212, pinyinSurface.pinyinBarWidthPx)
    }

    private fun surfaceInput(
        candidates: List<String>,
        candidateVisualWidthPx: Int?,
        fallbackViewportWidthPx: Int? = null
    ): T9CandidateSurfaceGeometry.SurfaceInput =
        T9CandidateSurfaceGeometry.SurfaceInput(
            candidates = FcitxEvent.PagedCandidateEvent.Data(
                candidates = candidates.map {
                    FcitxEvent.Candidate(label = "", text = it, comment = "")
                }.toTypedArray(),
                cursorIndex = 0,
                layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
                hasPrev = false,
                hasNext = false
            ),
            metrics = T9CandidateSurfaceGeometry.Metrics(
                maxRowWidthPx = 300,
                candidateSpacingPx = 4,
                candidateHorizontalPaddingPx = 3,
                minimumCandidateWidthPx = 10,
                rowHorizontalPaddingPx = 5,
                trailingPaddingPx = 7,
                showPaginationArrows = true,
                paginationWidthPx = 20,
                pinyinChipHorizontalPaddingPx = 2,
                pinyinChipSpacingPx = 4,
                pinyinOverflowHintTextWidthPx = 10,
                pinyinOverflowHintSpacingPx = 4,
                pinyinFoldedEdgeSafetyPx = 2,
                minVisiblePinyinChips = 4
            ),
            candidateVisualWidthPx = candidateVisualWidthPx,
            pinyinState = T9PinyinRowWindow.VisibleState(
                items = listOf("gei", "hei", "ge", "he", "g", "h", "i"),
                highlightedIndex = 0,
                windowStart = 0
            ),
            renderedPinyinItems = listOf("gei", "hei", "ge", "he", "g", "h", "i"),
            pinyinFallbackViewportWidthPx = fallbackViewportWidthPx,
            pinyinRowFocused = false
        )
}

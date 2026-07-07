/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class T9CandidateRowWidthCalculatorTest {
    @Test
    fun shortCandidatePageUsesContentWidth() {
        val width = T9CandidateRowWidthCalculator.calculate(
            input(candidates = listOf("好", "的"))
        )

        assertEquals(70, width)
    }

    @Test
    fun paginationAddsArrowReserve() {
        val width = T9CandidateRowWidthCalculator.calculate(
            input(
                candidates = listOf("好", "的"),
                hasNext = true
            )
        )

        assertEquals(90, width)
    }

    @Test
    fun widthIsCappedAtMaximumRowWidth() {
        val width = T9CandidateRowWidthCalculator.calculate(
            input(
                candidates = List(10) { "long" },
                maxWidthPx = 120
            )
        )

        assertEquals(120, width)
    }

    @Test
    fun emptyPageHasNoWidth() {
        assertNull(T9CandidateRowWidthCalculator.calculate(input(candidates = emptyList())))
    }

    private fun input(
        candidates: List<String>,
        maxWidthPx: Int = 300,
        hasPrev: Boolean = false,
        hasNext: Boolean = false
    ): T9CandidateRowWidthCalculator.Input =
        T9CandidateRowWidthCalculator.Input(
            data = FcitxEvent.PagedCandidateEvent.Data(
                candidates = candidates.map {
                    FcitxEvent.Candidate(label = "", text = it, comment = "")
                }.toTypedArray(),
                cursorIndex = 0,
                layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
                hasPrev = hasPrev,
                hasNext = hasNext
            ),
            widthBudget = T9CandidateWidthBudget(
                maxWidthPx = maxWidthPx,
                candidateSpacingPx = 4,
                candidateHorizontalPaddingPx = 3,
                minimumCandidateWidthPx = 10,
                activeScalePercent = 100,
                measureTextWidthPx = { it.length * 20 }
            ),
            rowHorizontalPaddingPx = 5,
            paginationWidthPx = 20
        )
}

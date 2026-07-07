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

        assertEquals(66, width)
    }

    @Test
    fun paginationAddsArrowReserve() {
        val width = T9CandidateRowWidthCalculator.calculate(
            input(
                candidates = listOf("好", "的"),
                showPaginationArrows = true,
                hasNext = true
            )
        )

        assertEquals(86, width)
    }

    @Test
    fun hiddenPaginationDoesNotReserveArrowWidth() {
        val width = T9CandidateRowWidthCalculator.calculate(
            input(
                candidates = listOf("好", "的"),
                showPaginationArrows = false,
                hasNext = true
            )
        )

        assertEquals(66, width)
    }

    @Test
    fun focusScaleDoesNotChangeLayoutWidth() {
        val width = T9CandidateRowWidthCalculator.calculate(
            input(
                candidates = listOf("好", "的", "长词"),
                activeScalePercent = 150
            )
        )

        assertEquals(116, width)
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
        showPaginationArrows: Boolean = true,
        hasPrev: Boolean = false,
        hasNext: Boolean = false,
        activeScalePercent: Int = 100
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
                activeScalePercent = activeScalePercent,
                measureTextWidthPx = { it.length * 20 }
            ),
            rowHorizontalPaddingPx = 5,
            showPaginationArrows = showPaginationArrows,
            paginationWidthPx = 20
        )
}

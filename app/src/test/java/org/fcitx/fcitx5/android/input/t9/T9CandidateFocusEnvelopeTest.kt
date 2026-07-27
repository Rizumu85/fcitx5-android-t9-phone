/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9CandidateFocusEnvelopeTest {
    @Test
    fun leadingCandidateReservesAllGrowthTowardFollowingCandidate() {
        val margins = T9CandidateFocusEnvelope.candidateEndMarginsPx(
            candidateWidthsPx = listOf(100, 20),
            itemSpacingPx = 4,
            hasTrailingItem = false,
            scalePercent = 150
        )

        assertEquals(listOf(54, 0), margins)
    }

    @Test
    fun paginationUsesLastFocusGrowthAsItsBoundaryReserve() {
        val margins = T9CandidateFocusEnvelope.candidateEndMarginsPx(
            candidateWidthsPx = listOf(40),
            itemSpacingPx = 4,
            hasTrailingItem = true,
            scalePercent = 150
        )

        assertEquals(listOf(24), margins)
    }

    @Test
    fun trailingCandidateGrowsInwardWithoutChangingBubbleTailInset() {
        val margins = T9CandidateFocusEnvelope.candidateEndMarginsPx(
            candidateWidthsPx = listOf(40, 100),
            itemSpacingPx = 4,
            hasTrailingItem = false,
            scalePercent = 150
        )

        assertEquals(listOf(54, 0), margins)
    }

    @Test
    fun singleCandidateReservesItsFocusedGrowthBeforeTheBubbleTail() {
        val margins = T9CandidateFocusEnvelope.candidateEndMarginsPx(
            candidateWidthsPx = listOf(100),
            itemSpacingPx = 4,
            hasTrailingItem = false,
            scalePercent = 150
        )

        assertEquals(listOf(50), margins)
    }

    @Test
    fun focusedCandidateWidthFitsInsideTheWholeRowEnvelope() {
        val width = T9CandidateFocusEnvelope.maxUnfocusedCandidateWidthPx(
            maxRowWidthPx = 300,
            edgePaddingPx = 5,
            scalePercent = 150
        )

        assertEquals(193, width)
    }
}

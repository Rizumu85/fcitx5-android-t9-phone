/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateRowLayoutTest {
    @Test
    fun rowWidthIncludesOnlyRealGapsAndPadding() {
        assertEquals(
            10 + 20 + 30 + 4 + 4 + 4,
            T9CandidateRowLayout.rowWidth(
                itemWidths = listOf(10, 20, 30),
                itemSpacingPx = 4,
                paginationWidthPx = 0,
                hasPagination = false,
                sidePaddingPx = 4
            )
        )
    }

    @Test
    fun rowWidthAddsGapBeforePaginationOnlyWhenVisible() {
        assertEquals(
            10 + 20 + 4 + 4 + 12 + 6,
            T9CandidateRowLayout.rowWidth(
                itemWidths = listOf(10, 20),
                itemSpacingPx = 4,
                paginationWidthPx = 12,
                hasPagination = true,
                sidePaddingPx = 6
            )
        )
    }

    @Test
    fun paginationIsVisibleAtEitherPageEdge() {
        assertFalse(T9CandidateRowLayout.paginationVisible(hasPrev = false, hasNext = false))
        assertTrue(T9CandidateRowLayout.paginationVisible(hasPrev = true, hasNext = false))
        assertTrue(T9CandidateRowLayout.paginationVisible(hasPrev = false, hasNext = true))
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9ShortcutTailPolicyTest {
    @Test
    fun onlyTheLastVisibleCandidateEdgeAlignsToTheBubbleTail() {
        assertTrue(
            T9ShortcutTailPolicy.edgeAlignsCandidateToBubbleTail(
                isCandidate = true,
                isLastVisibleItem = true
            )
        )
        assertFalse(
            T9ShortcutTailPolicy.edgeAlignsCandidateToBubbleTail(
                isCandidate = true,
                isLastVisibleItem = false
            )
        )
        assertFalse(
            T9ShortcutTailPolicy.edgeAlignsCandidateToBubbleTail(
                isCandidate = false,
                isLastVisibleItem = true
            )
        )
    }

    @Test
    fun reservesTailForScaledLastShortcutChip() {
        val width = T9ShortcutTailPolicy.stabilizedToolbarWidthPx(
            naturalWidthPx = 110,
            lastChildMeasuredRightPx = 100,
            lastChildMeasuredWidthPx = 50,
            lastChildScaleX = 1.07f,
            edgePaddingPx = 3,
            trailingPaddingPx = 7,
            maxRowWidthPx = 200
        )

        assertEquals(112, width)
    }

    @Test
    fun plannedToolbarWidthUsesFixedTailAndFocusOverflow() {
        val width = T9ShortcutTailPolicy.plannedToolbarWidthPx(
            candidateContentWidthPx = 56,
            tailScaleOverflowPx = 7,
            edgePaddingPx = 5,
            trailingPaddingPx = 4,
            maxRowWidthPx = 200
        )

        assertEquals(77, width)
    }

    @Test
    fun neverShrinksNaturalWidthOrExceedsTheRowBudget() {
        val alreadyWideEnough = T9ShortcutTailPolicy.stabilizedToolbarWidthPx(
            naturalWidthPx = 140,
            lastChildMeasuredRightPx = 100,
            lastChildMeasuredWidthPx = 50,
            lastChildScaleX = 1f,
            edgePaddingPx = 3,
            trailingPaddingPx = 7,
            maxRowWidthPx = 200
        )
        val capped = T9ShortcutTailPolicy.stabilizedToolbarWidthPx(
            naturalWidthPx = 140,
            lastChildMeasuredRightPx = 180,
            lastChildMeasuredWidthPx = 100,
            lastChildScaleX = 1.2f,
            edgePaddingPx = 10,
            trailingPaddingPx = 10,
            maxRowWidthPx = 190
        )

        assertEquals(140, alreadyWideEnough)
        assertEquals(190, capped)
    }
}

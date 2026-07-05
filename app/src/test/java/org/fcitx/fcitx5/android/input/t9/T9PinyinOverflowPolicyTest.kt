/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9PinyinOverflowPolicyTest {
    @Test
    fun keepsAllPinyinChipsWhenCandidateRowCanHoldThem() {
        assertFalse(
            T9PinyinOverflowPolicy.shouldFold(
                pinyinCount = 7,
                fullContentWidthPx = 240,
                candidateRowWidthPx = 260,
                maxRowWidthPx = 320,
                minVisibleChips = 4
            )
        )
    }

    @Test
    fun foldsOnlyWhenShortCandidateRowCannotHoldPinyinChips() {
        assertTrue(
            T9PinyinOverflowPolicy.shouldFold(
                pinyinCount = 7,
                fullContentWidthPx = 240,
                candidateRowWidthPx = 120,
                maxRowWidthPx = 320,
                minVisibleChips = 4
            )
        )
    }

    @Test
    fun doesNotFoldFourOrFewerPinyinChips() {
        assertFalse(
            T9PinyinOverflowPolicy.shouldFold(
                pinyinCount = 4,
                fullContentWidthPx = 240,
                candidateRowWidthPx = 120,
                maxRowWidthPx = 320,
                minVisibleChips = 4
            )
        )
    }

    @Test
    fun bottomFocusShowsFourChipsAndHintWhenFolded() {
        val plan = T9PinyinOverflowPolicy.plan(
            pinyinCount = 7,
            fullContentWidthPx = 240,
            candidateRowWidthPx = 120,
            maxRowWidthPx = 320,
            minVisibleChips = 4,
            pinyinRowFocused = false
        )

        assertTrue(plan.folded)
        assertTrue(plan.showHint)
        assertEquals(4, plan.visibleCount)
    }

    @Test
    fun topFocusKeepsFoldedWidthButLetsRowContainAllChipsForScrolling() {
        val plan = T9PinyinOverflowPolicy.plan(
            pinyinCount = 7,
            fullContentWidthPx = 240,
            candidateRowWidthPx = 120,
            maxRowWidthPx = 320,
            minVisibleChips = 4,
            pinyinRowFocused = true
        )

        assertTrue(plan.folded)
        assertFalse(plan.showHint)
        assertEquals(7, plan.visibleCount)
    }
}

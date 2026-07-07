/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateSurfaceLayoutTest {

    @Test
    fun waitsForMeasuredCandidateWidth() {
        val plan = T9CandidateSurfaceLayout.plan(input(candidateMeasuredWidthPx = null))

        assertFalse(plan.contentReady)
        assertNull(plan.rowWidthPx)
        assertNull(plan.pinyin)
    }

    @Test
    fun usesMeasuredCandidateWidthWhenPinyinFits() {
        val plan = T9CandidateSurfaceLayout.plan(
            input(
                candidateMeasuredWidthPx = 240,
                pinyinFullContentWidthPx = 180
            )
        )

        assertTrue(plan.contentReady)
        assertEquals(240, plan.rowWidthPx)
        assertNotNull(plan.pinyin)
        assertFalse(plan.pinyin!!.folded)
    }

    @Test
    fun nonFoldedPinyinDoesNotWidenShortHanziPages() {
        val plan = T9CandidateSurfaceLayout.plan(
            input(
                candidateMeasuredWidthPx = 90,
                pinyinCount = 4,
                pinyinFullContentWidthPx = 260,
                pinyinFoldedContentWidthPx = 150
            )
        )

        assertEquals(90, plan.rowWidthPx)
        val pinyin = requireNotNull(plan.pinyin)
        assertFalse(pinyin.folded)
        assertFalse(pinyin.showHint)
        assertEquals(4, pinyin.visibleCount)
    }

    @Test
    fun letsFoldedPinyinWidthDriveShortHanziPages() {
        val plan = T9CandidateSurfaceLayout.plan(
            input(
                candidateMeasuredWidthPx = 80,
                pinyinFullContentWidthPx = 260,
                pinyinFoldedContentWidthPx = 150
            )
        )

        assertEquals(150, plan.rowWidthPx)
        val pinyin = requireNotNull(plan.pinyin)
        assertTrue(pinyin.folded)
        assertTrue(pinyin.showHint)
        assertEquals(4, pinyin.visibleCount)
    }

    @Test
    fun focusedFoldedPinyinUsesHanziBubbleWidth() {
        val plan = T9CandidateSurfaceLayout.plan(
            input(
                candidateMeasuredWidthPx = 80,
                pinyinFullContentWidthPx = 260,
                pinyinFoldedContentWidthPx = 150,
                pinyinRowFocused = true
            )
        )

        assertEquals(80, plan.rowWidthPx)
        val pinyin = requireNotNull(plan.pinyin)
        assertTrue(pinyin.folded)
        assertFalse(pinyin.showHint)
        assertEquals(7, pinyin.visibleCount)
    }

    private fun input(
        candidateMeasuredWidthPx: Int? = 200,
        pinyinCount: Int = 7,
        pinyinFullContentWidthPx: Int = 240,
        pinyinFoldedContentWidthPx: Int = 140,
        maxRowWidthPx: Int = 320,
        minVisiblePinyinChips: Int = 4,
        pinyinRowFocused: Boolean = false
    ): T9CandidateSurfaceLayout.Input =
        T9CandidateSurfaceLayout.Input(
            candidateMeasuredWidthPx = candidateMeasuredWidthPx,
            pinyinCount = pinyinCount,
            pinyinFullContentWidthPx = pinyinFullContentWidthPx,
            pinyinFoldedContentWidthPx = pinyinFoldedContentWidthPx,
            maxRowWidthPx = maxRowWidthPx,
            minVisiblePinyinChips = minVisiblePinyinChips,
            pinyinRowFocused = pinyinRowFocused
        )
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidatePagerTest {

    @Test
    fun splitsCandidatesByCharacterBudgetAndPreservesOriginalIndices() {
        val pager = T9CandidatePager()
        pager.update(
            signature = "initial",
            candidates = listOf(
                IndexedValue(3, candidate("一二三")),
                IndexedValue(5, candidate("四五")),
                IndexedValue(8, candidate("六七八"))
            ),
            characterBudget = 4
        )

        val first = pager.currentPage()
        assertNotNull(first)
        first!!
        assertEquals(listOf("一二三"), first.candidates.map { it.value.text })
        assertArrayEquals(intArrayOf(3), first.originalIndices)
        assertFalse(first.hasPrev)
        assertTrue(first.hasNext)

        val second = pager.offset(1)
        assertNotNull(second)
        second!!
        assertEquals(listOf("四五"), second.candidates.map { it.value.text })
        assertArrayEquals(intArrayOf(5), second.originalIndices)
        assertTrue(second.hasPrev)
        assertTrue(second.hasNext)
    }

    @Test
    fun selectsPageContainingOriginalIndex() {
        val pager = T9CandidatePager()
        pager.update(
            signature = "initial",
            candidates = listOf(
                IndexedValue(0, candidate("一二三")),
                IndexedValue(1, candidate("四五")),
                IndexedValue(2, candidate("六七八"))
            ),
            characterBudget = 4
        )

        val page = pager.selectPageContainingOriginalIndex(2)
        assertNotNull(page)
        page!!

        assertEquals(listOf("六七八"), page.candidates.map { it.value.text })
        assertEquals(2, pager.pageIndex)
    }

    @Test
    fun pageBuildsPagedCandidatesWithOriginalIndices() {
        val pager = T9CandidatePager()
        pager.update(
            signature = "initial",
            candidates = listOf(
                IndexedValue(7, candidate("一二三")),
                IndexedValue(9, candidate("四五")),
                IndexedValue(11, candidate("六"))
            ),
            characterBudget = 4
        )

        val second = pager.offset(1)
        assertNotNull(second)
        second!!

        val shown = second.toPagedCandidates(
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            cursorIndex = second.cursorIndexForOriginalIndex(11),
            hasExternalNext = true
        )

        assertEquals(listOf("四五", "六"), shown.data.candidates.map { it.text })
        assertEquals(1, shown.data.cursorIndex)
        assertTrue(shown.data.hasPrev)
        assertTrue(shown.data.hasNext)
        assertArrayEquals(intArrayOf(9, 11), shown.originalIndices)
    }

    @Test
    fun capsPageSizeToPhysicalShortcutCount() {
        val pager = T9CandidatePager()
        val shortWords = listOf(
            "and", "ane", "bod", "boe", "cod", "coe",
            "amd", "ame", "bof", "con", "boo", "bon"
        )
        pager.update(
            signature = "short-english",
            candidates = shortWords.mapIndexed { index, word -> IndexedValue(index, candidate(word)) },
            characterBudget = 24
        )

        val first = pager.currentPage()
        assertNotNull(first)
        first!!
        assertEquals(10, first.candidates.size)
        assertTrue(first.hasNext)

        val second = pager.offset(1)
        assertNotNull(second)
        second!!
        assertEquals(listOf("boo", "bon"), second.candidates.map { it.value.text })
        assertArrayEquals(intArrayOf(10, 11), second.originalIndices)
    }

    @Test
    fun keepsShortCandidatesStableWhenTheyFitPixelBudget() {
        val pager = T9CandidatePager()
        val shortWords = listOf(
            "and", "ane", "bod", "boe", "cod", "coe",
            "amd", "ame", "bof", "con", "boo", "bon"
        )
        pager.update(
            signature = "short-english",
            candidates = shortWords.mapIndexed { index, word -> IndexedValue(index, candidate(word)) },
            characterBudget = 24,
            widthBudget = widthBudget(maxWidthPx = 1_000)
        )

        val first = pager.currentPage()
        assertNotNull(first)
        first!!

        assertEquals(10, first.candidates.size)
        assertEquals(shortWords.take(10), first.candidates.map { it.value.text })
        assertTrue(first.hasNext)
    }

    @Test
    fun splitsLongEnglishCandidatesByPixelBudget() {
        val pager = T9CandidatePager()
        val words = listOf("bodily", "Boeing", "coding", "AMDgpu", "Command", "condition")
        pager.update(
            signature = "long-english",
            candidates = words.mapIndexed { index, word -> IndexedValue(index, candidate(word)) },
            characterBudget = 24,
            widthBudget = widthBudget(maxWidthPx = 60)
        )

        val first = pager.currentPage()
        assertNotNull(first)
        first!!

        assertEquals(listOf("bodily", "Boeing"), first.candidates.map { it.value.text })
        assertArrayEquals(intArrayOf(0, 1), first.originalIndices)
        assertTrue(first.hasNext)

        val second = pager.offset(1)
        assertNotNull(second)
        second!!
        assertEquals(listOf("coding", "AMDgpu"), second.candidates.map { it.value.text })
        assertArrayEquals(intArrayOf(2, 3), second.originalIndices)
    }

    @Test
    fun keepsSingleOversizedCandidateVisibleForEllipsizing() {
        val pager = T9CandidatePager()
        pager.update(
            signature = "oversized",
            candidates = listOf(
                IndexedValue(0, candidate("supercalifragilistic")),
                IndexedValue(1, candidate("word"))
            ),
            characterBudget = 24,
            widthBudget = widthBudget(maxWidthPx = 20)
        )

        val first = pager.currentPage()
        assertNotNull(first)
        first!!

        assertEquals(listOf("supercalifragilistic"), first.candidates.map { it.value.text })
        assertTrue(first.hasNext)
    }

    @Test
    fun pinsSemanticActionToEndOfFirstPageWithoutBreakingOriginalIndices() {
        val pager = T9CandidatePager()
        val candidates = listOf("，", "。", "？", "！", "、", "：", "；", "…", "·", "“", "↵")
            .mapIndexed { index, text -> IndexedValue(index, candidate(text)) }
        pager.update(
            signature = "punctuation",
            candidates = candidates,
            characterBudget = 10,
            pinnedFirstPageTailOriginalIndex = 10
        )

        val first = pager.currentPage()!!
        assertEquals(listOf("，", "。", "？", "！", "、", "：", "；", "…", "·", "↵"), first.candidates.map { it.value.text })
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 10), first.originalIndices)

        val second = pager.offset(1)!!
        assertEquals(listOf("“"), second.candidates.map { it.value.text })
        assertArrayEquals(intArrayOf(9), second.originalIndices)
    }

    private fun candidate(text: String): FcitxEvent.Candidate =
        FcitxEvent.Candidate(label = "", text = text, comment = "")

    private fun widthBudget(maxWidthPx: Int): T9CandidateWidthBudget =
        T9CandidateWidthBudget(
            maxWidthPx = maxWidthPx,
            candidateSpacingPx = 0,
            candidateHorizontalPaddingPx = 0,
            minimumCandidateWidthPx = 1,
            activeScalePercent = 100,
            measureTextWidthPx = { it.length * 5 }
        )
}

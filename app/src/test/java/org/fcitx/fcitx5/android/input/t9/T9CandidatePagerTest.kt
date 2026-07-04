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
import org.junit.Assert.assertNull
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
    fun splitsCandidatesByMeasuredBudget() {
        val pager = T9CandidatePager()
        val widths = mapOf("hello" to 4, "hi" to 4, "longword" to 4)
        pager.update(
            signature = "measured",
            candidates = listOf(
                IndexedValue(0, candidate("hello")),
                IndexedValue(1, candidate("hi")),
                IndexedValue(2, candidate("longword"))
            ),
            budget = T9CandidatePager.Budget(
                value = 10,
                key = "10px",
                itemCost = { candidate -> widths.getValue(candidate.text) }
            )
        )

        val first = pager.currentPage()
        assertNotNull(first)
        first!!
        assertEquals(listOf("hello", "hi"), first.candidates.map { it.value.text })
        assertArrayEquals(intArrayOf(0, 1), first.originalIndices)
        assertFalse(first.hasPrev)
        assertTrue(first.hasNext)

        val second = pager.offset(1)
        assertNotNull(second)
        second!!
        assertEquals(listOf("longword"), second.candidates.map { it.value.text })
        assertArrayEquals(intArrayOf(2), second.originalIndices)
        assertTrue(second.hasPrev)
        assertFalse(second.hasNext)
    }

    @Test
    fun protectedSingleUnitCandidatesCanFillConfiguredCapacityWithinHardBudget() {
        val pager = T9CandidatePager()
        pager.update(
            signature = "protected",
            candidates = (0 until 11).map { index ->
                IndexedValue(index, candidate(index.toString()))
            },
            budget = T9CandidatePager.Budget(
                value = 90,
                key = "protected",
                itemCost = { 10 },
                hardValue = 100,
                hardItemCost = { 10 },
                protectedMinItems = 10,
                canProtectItem = { it.text.length == 1 }
            )
        )

        val first = pager.currentPage()
        assertNotNull(first)
        first!!
        assertEquals((0 until 10).map { it.toString() }, first.candidates.map { it.value.text })
        assertTrue(first.hasNext)
    }

    @Test
    fun protectedEmojiCandidatesCanFillConfiguredCapacityWithinHardBudget() {
        val pager = T9CandidatePager()
        val emoji = listOf("😀", "☺️", "👍🏽", "1️⃣", "🎉", "❤️", "😂", "🥹", "🚀", "✨", "🔥")
        pager.update(
            signature = "emoji",
            candidates = emoji.mapIndexed { index, text -> IndexedValue(index, candidate(text)) },
            budget = T9CandidatePager.Budget(
                value = 90,
                key = "emoji",
                itemCost = { 10 },
                hardValue = 100,
                hardItemCost = { 10 },
                protectedMinItems = 10,
                canProtectItem = { T9CandidateBudget.candidateCost(it.text) == 1 }
            )
        )

        val first = pager.currentPage()
        assertNotNull(first)
        first!!
        assertEquals(emoji.take(10), first.candidates.map { it.value.text })
        assertTrue(first.hasNext)
    }

    @Test
    fun measuredBudgetIncludesRowSpacingSidePaddingAndPagination() {
        val pager = T9CandidatePager()
        pager.update(
            signature = "row",
            candidates = (0 until 4).map { index -> IndexedValue(index, candidate(index.toString())) },
            budget = T9CandidatePager.Budget(
                value = 68,
                key = "row",
                itemCost = { 20 },
                itemSpacing = 4,
                paginationCost = 12,
                sidePadding = 4
            )
        )

        val first = pager.currentPage()
        assertNotNull(first)
        first!!
        assertEquals(listOf("0", "1"), first.candidates.map { it.value.text })
        assertTrue(first.hasNext)

        val second = pager.offset(1)
        assertNotNull(second)
        second!!
        assertEquals(listOf("2", "3"), second.candidates.map { it.value.text })
        assertTrue(second.hasPrev)
        assertFalse(second.hasNext)
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
    fun offsetPastLastPageReturnsNull() {
        val pager = T9CandidatePager()
        pager.update(
            signature = "initial",
            candidates = listOf(
                IndexedValue(0, candidate("一二三")),
                IndexedValue(1, candidate("四五"))
            ),
            characterBudget = 4
        )

        assertNotNull(pager.currentPage())
        val second = pager.offset(1)
        assertNotNull(second)
        assertFalse(second!!.hasNext)
        assertNull(pager.offset(1))
    }

    private fun candidate(text: String): FcitxEvent.Candidate =
        FcitxEvent.Candidate(label = "", text = text, comment = "")
}

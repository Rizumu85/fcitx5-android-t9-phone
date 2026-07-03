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

    private fun candidate(text: String): FcitxEvent.Candidate =
        FcitxEvent.Candidate(label = "", text = text, comment = "")
}

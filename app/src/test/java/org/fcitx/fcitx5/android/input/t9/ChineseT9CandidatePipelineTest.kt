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

class ChineseT9CandidatePipelineTest {

    @Test
    fun localBudgetPageKeepsPagerStateAndOriginalIndices() {
        val pipeline = pipeline(characterBudget = 4)
        val raw = paged(
            candidate("一二三"),
            candidate("四五"),
            candidate("六")
        )

        val first = pipeline.buildLocalBudgetedPagedFromCurrentPage(raw)

        assertNotNull(first)
        first!!
        assertEquals(listOf("一二三"), first.data.candidates.map { it.text })
        assertArrayEquals(intArrayOf(0), first.originalIndices)
        assertFalse(first.data.hasPrev)
        assertTrue(first.data.hasNext)

        assertTrue(pipeline.offsetLocalBudgetedPage(1))
        val second = pipeline.buildLocalBudgetedPagedFromCurrentPage(raw)

        assertNotNull(second)
        second!!
        assertEquals(listOf("四五", "六"), second.data.candidates.map { it.text })
        assertArrayEquals(intArrayOf(1, 2), second.originalIndices)
        assertTrue(second.data.hasPrev)
        assertFalse(second.data.hasNext)
        assertFalse(pipeline.offsetLocalBudgetedPage(1))
    }

    @Test
    fun localBudgetPageReturnsNullWhenRawPageAlreadyFits() {
        val pipeline = pipeline(characterBudget = 4)
        val raw = paged(candidate("一"), candidate("二"))

        assertNull(pipeline.buildLocalBudgetedPagedFromCurrentPage(raw))
        assertFalse(pipeline.hasLocalBudgetCandidates)
    }

    @Test
    fun pinyinPrefixFilteringCarriesOriginalIndices() {
        val pipeline = pipeline()
        val raw = paged(
            candidate("你", comment = "ni"),
            candidate("呢", comment = "ne"),
            candidate("泥", comment = "ni")
        )
        val (shown, matchedPrefix) = pipeline.filterPagedByPinyinPrefixes(raw, listOf("ni"))

        assertEquals("ni", matchedPrefix)
        assertEquals(listOf("你", "泥"), shown.data.candidates.map { it.text })
        assertArrayEquals(intArrayOf(0, 2), shown.originalIndices)
    }

    @Test
    fun pinyinPrefixFilteringReusesSameCandidateSnapshot() {
        var matchCalls = 0
        val pipeline = ChineseT9CandidatePipeline(
            characterBudget = { 10 },
            candidateMatchesPrefix = { candidate, prefix ->
                matchCalls += 1
                candidate.comment == prefix
            }
        )
        val raw = paged(
            candidate("你", comment = "ni"),
            candidate("呢", comment = "ne"),
            candidate("泥", comment = "ni")
        )

        val first = pipeline.filterPagedByPinyinPrefixes(raw, listOf("ni"))
        val second = pipeline.filterPagedByPinyinPrefixes(raw, listOf("ni"))

        assertEquals(listOf("你", "泥"), first.first.data.candidates.map { it.text })
        assertEquals(listOf("你", "泥"), second.first.data.candidates.map { it.text })
        assertEquals(3, matchCalls)
    }

    @Test
    fun hanziCursorPersistsAcrossSameContextAndResetsWhenContextChanges() {
        val pipeline = pipeline()
        val raw = paged(
            candidate("你"),
            candidate("好"),
            candidate("呢"),
            cursorIndex = 2
        )

        val initial = pipeline.applyHanziCursor(raw, cursorContextSignature = "ni")
        assertEquals(0, initial.cursorIndex)

        val moved = pipeline.moveHanziCursor(initial, 2)
        assertNotNull(moved)
        moved!!
        assertEquals(2, moved.cursorIndex)

        val sameContext = pipeline.applyHanziCursor(moved, cursorContextSignature = "ni")
        assertEquals(2, sameContext.cursorIndex)

        val changedContext = pipeline.applyHanziCursor(sameContext, cursorContextSignature = "hao")
        assertEquals(0, changedContext.cursorIndex)
    }

    @Test
    fun pinyinPrefixFilteringUsesFirstMatchingPrefixAndBudgetsTheShownPage() {
        val pipeline = pipeline(
            characterBudget = 4,
            matchesPrefix = { candidate, prefix -> candidate.comment.startsWith(prefix) }
        )
        val raw = paged(
            candidate("一", comment = "ni"),
            candidate("二", comment = "niang"),
            candidate("三", comment = "nong"),
            candidate("四", comment = "nu"),
            candidate("五", comment = "na")
        )

        val (filtered, matchedPrefix) = pipeline.filterPagedByPinyinPrefixes(raw, listOf("zh", "n"))

        assertEquals("n", matchedPrefix)
        assertEquals(listOf("一", "二", "三", "四"), filtered.data.candidates.map { it.text })
        assertArrayEquals(intArrayOf(0, 1, 2, 3), filtered.originalIndices)
        assertEquals(0, filtered.data.cursorIndex)
        assertFalse(filtered.data.hasPrev)
        assertTrue(filtered.data.hasNext)
    }

    private fun pipeline(
        characterBudget: Int = 24,
        matchesPrefix: (FcitxEvent.Candidate, String) -> Boolean = { candidate, prefix ->
            candidate.comment.startsWith(prefix)
        }
    ): ChineseT9CandidatePipeline =
        ChineseT9CandidatePipeline(
            characterBudget = { characterBudget },
            candidateMatchesPrefix = matchesPrefix
        )

    private fun paged(
        vararg candidates: FcitxEvent.Candidate,
        cursorIndex: Int = 0,
        hasPrev: Boolean = false,
        hasNext: Boolean = false
    ): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = candidates.toList().toTypedArray(),
            cursorIndex = cursorIndex,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = hasPrev,
            hasNext = hasNext
        )

    private fun candidate(
        text: String,
        label: String = "",
        comment: String = ""
    ): FcitxEvent.Candidate =
        FcitxEvent.Candidate(label = label, text = text, comment = comment)
}

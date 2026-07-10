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
import org.junit.Assert.assertSame
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

        val first = pipeline.buildLocalBudgetedPagedFromCurrentPage(source(raw))

        assertNotNull(first)
        first!!
        assertEquals(listOf("一二三"), first.data.candidates.map { it.text })
        assertArrayEquals(intArrayOf(0), first.originalIndices)
        assertFalse(first.data.hasPrev)
        assertTrue(first.data.hasNext)

        assertTrue(pipeline.offsetLocalBudgetedPage(1))
        val second = pipeline.buildLocalBudgetedPagedFromCurrentPage(source(raw))

        assertNotNull(second)
        second!!
        assertEquals(listOf("四五", "六"), second.data.candidates.map { it.text })
        assertArrayEquals(intArrayOf(1, 2), second.originalIndices)
        assertTrue(second.data.hasPrev)
        assertFalse(second.data.hasNext)
        assertFalse(pipeline.offsetLocalBudgetedPage(1))
    }

    @Test
    fun localBudgetPageKeepsSourceIndicesAfterEligibilityFiltering() {
        val pipeline = pipeline(characterBudget = 4)
        val filteredSource = T9PagedCandidates(
            data = paged(
                candidate("一"),
                candidate("下"),
                candidate("才"),
                candidate("工"),
                candidate("土")
            ),
            originalIndices = intArrayOf(0, 2, 3, 4, 5)
        )

        pipeline.buildLocalBudgetedPagedFromCurrentPage(filteredSource)
        assertTrue(pipeline.offsetLocalBudgetedPage(1))
        val second = pipeline.buildLocalBudgetedPagedFromCurrentPage(filteredSource)

        assertNotNull(second)
        assertEquals(listOf("土"), second!!.data.candidates.map { it.text })
        assertArrayEquals(intArrayOf(5), second.originalIndices)
    }

    @Test
    fun localBudgetPageReturnsNullWhenRawPageAlreadyFits() {
        val pipeline = pipeline(characterBudget = 4)
        val raw = paged(candidate("一"), candidate("二"))

        assertNull(pipeline.buildLocalBudgetedPagedFromCurrentPage(source(raw)))
        assertFalse(pipeline.hasLocalBudgetCandidates)
    }

    @Test
    fun localBudgetPageReusesSameInputResultAcrossRefreshes() {
        val pipeline = pipeline(characterBudget = 4)
        val raw = paged(
            candidate("一二三"),
            candidate("四五"),
            candidate("六")
        )

        val first = pipeline.buildLocalBudgetedPagedFromCurrentPage(source(raw))
        val repeated = pipeline.buildLocalBudgetedPagedFromCurrentPage(source(raw))

        assertSame(first, repeated)
    }

    @Test
    fun resettingPinyinBulkStateDoesNotEraseLocalPage() {
        val pipeline = pipeline(characterBudget = 4)
        val raw = paged(
            candidate("一二三"),
            candidate("四五"),
            candidate("六")
        )
        pipeline.buildLocalBudgetedPagedFromCurrentPage(source(raw))
        assertTrue(pipeline.offsetLocalBudgetedPage(1))

        pipeline.resetBulkFilter()
        val repeated = pipeline.buildLocalBudgetedPagedFromCurrentPage(source(raw))

        assertNotNull(repeated)
        assertEquals(listOf("四五", "六"), repeated!!.data.candidates.map { it.text })
    }

    @Test
    fun pinyinPrefixFilteringCarriesOriginalIndices() {
        val pipeline = pipeline()
        val raw = paged(
            candidate("你", comment = "ni"),
            candidate("呢", comment = "ne"),
            candidate("泥", comment = "ni")
        )
        val (shown, matchedPrefix) = pipeline.filterPagedByReadingPrefixes(raw, listOf("ni"))

        assertEquals("ni", matchedPrefix)
        assertEquals(listOf("你", "泥"), shown.data.candidates.map { it.text })
        assertArrayEquals(intArrayOf(0, 2), shown.originalIndices)
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

        val (filtered, matchedPrefix) = pipeline.filterPagedByReadingPrefixes(raw, listOf("zh", "n"))

        assertEquals("n", matchedPrefix)
        assertEquals(listOf("一", "二", "三", "四"), filtered.data.candidates.map { it.text })
        assertArrayEquals(intArrayOf(0, 1, 2, 3), filtered.originalIndices)
        assertEquals(0, filtered.data.cursorIndex)
        assertFalse(filtered.data.hasPrev)
        assertTrue(filtered.data.hasNext)
    }

    @Test
    fun bulkFilterStateOwnsPagedCandidatesAndOriginalIndices() {
        val pipeline = pipeline(characterBudget = 4)
        val signature = pipeline.bulkFilterRequestSignature(
            prefixes = listOf("ni"),
            preedit = "ni",
            candidates = emptyArray()
        )

        assertTrue(pipeline.shouldRequestBulkFilter(signature))
        pipeline.startBulkFilterRequest(listOf("ni"), signature)
        assertTrue(pipeline.bulkFilterState.pending)

        val state = pipeline.finishBulkFilterRequest(
            signature = signature,
            rawCandidates = listOf("你 ni", "泥 ni", "逆 ni", "拟 ni", "年 nian"),
            prefixes = listOf("ni"),
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal
        )

        assertNotNull(state)
        state!!
        assertFalse(state.pending)
        assertEquals("ni", state.matchedPrefix)
        assertEquals(listOf("你", "泥", "逆", "拟"), state.paged?.data?.candidates?.map { it.text })
        assertArrayEquals(intArrayOf(0, 1, 2, 3), state.paged?.originalIndices)

        assertTrue(pipeline.offsetBulkFilteredPage(1, FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal))
        val second = pipeline.bulkFilterState.paged
        assertEquals(listOf("年"), second?.data?.candidates?.map { it.text })
        assertArrayEquals(intArrayOf(4), second?.originalIndices)
    }

    @Test
    fun zhuyinReadingFilterMatchesNormalizedPhraseCommentsAcrossPages() {
        val resolver = T9ZhuyinResolver()
        val pipeline = pipeline(
            matchesPrefix = { candidate, prefix ->
                resolver.candidateMatchesReadingOption(candidate.comment, prefix)
            }
        )
        val signature = pipeline.bulkFilterRequestSignature(
            prefixes = listOf("ㄋㄧ ㄏㄠ"),
            preedit = "2038",
            candidates = emptyArray()
        )
        pipeline.startBulkFilterRequest(listOf("ㄋㄧ ㄏㄠ"), signature)

        val state = pipeline.finishBulkFilterRequest(
            signature = signature,
            rawCandidates = listOf(
                "你好 ㄋㄧ'ㄏㄠ",
                "拟好 ㄋㄧ'ㄏㄠ",
                "你高 ㄋㄧ'ㄍㄠ"
            ),
            prefixes = listOf("ㄋㄧ ㄏㄠ"),
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal
        )

        assertEquals(listOf("你好", "拟好"), state?.paged?.data?.candidates?.map { it.text })
        assertEquals("ㄋㄧ ㄏㄠ", state?.matchedPrefix)
    }

    private fun pipeline(
        characterBudget: Int = 24,
        matchesPrefix: (FcitxEvent.Candidate, String) -> Boolean = { candidate, prefix ->
            candidate.comment.startsWith(prefix)
        }
    ): ChineseT9CandidatePipeline =
        ChineseT9CandidatePipeline(
            characterBudget = { characterBudget },
            widthBudget = { null },
            candidateMatchesPrefix = matchesPrefix
        )

    private fun source(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates =
        T9PagedCandidates.passthrough(data)

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

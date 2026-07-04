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
        assertEquals(listOf("一二三"), first.candidates.map { it.text })
        assertArrayEquals(intArrayOf(0), pipeline.buildOriginalIndicesForPaged(first, raw))
        assertFalse(first.hasPrev)
        assertTrue(first.hasNext)

        assertTrue(pipeline.offsetLocalBudgetedPage(1))
        val second = pipeline.buildLocalBudgetedPagedFromCurrentPage(raw)

        assertNotNull(second)
        second!!
        assertEquals(listOf("四五", "六"), second.candidates.map { it.text })
        assertArrayEquals(intArrayOf(1, 2), pipeline.buildOriginalIndicesForPaged(second, raw))
        assertTrue(second.hasPrev)
        assertFalse(second.hasNext)
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
    fun originalIndicesTrackDuplicateCandidatesByOccurrence() {
        val pipeline = pipeline()
        val duplicated = candidate("重")
        val raw = paged(
            duplicated,
            candidate("中"),
            duplicated,
            candidate("终")
        )
        val shown = paged(duplicated, duplicated, candidate("终"))

        assertArrayEquals(
            intArrayOf(0, 2, 3),
            pipeline.buildOriginalIndicesForPaged(shown, raw)
        )
        assertEquals(
            2,
            pipeline.originalCandidateIndexForShown(
                shown = shown,
                shownIndex = 1,
                rawPaged = raw,
                shownOriginalIndices = intArrayOf(-1, -1, -1)
            )
        )
    }

    @Test
    fun explicitOriginalIndicesTakePriorityWhenSelectingShownCandidate() {
        val pipeline = pipeline()
        val raw = paged(candidate("甲"), candidate("乙"), candidate("丙"))
        val shown = paged(candidate("丙"))

        assertEquals(
            2,
            pipeline.originalCandidateIndexForShown(
                shown = shown,
                shownIndex = 0,
                rawPaged = raw,
                shownOriginalIndices = intArrayOf(2)
            )
        )
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
        assertEquals(listOf("一", "二", "三", "四"), filtered.candidates.map { it.text })
        assertEquals(0, filtered.cursorIndex)
        assertFalse(filtered.hasPrev)
        assertTrue(filtered.hasNext)
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

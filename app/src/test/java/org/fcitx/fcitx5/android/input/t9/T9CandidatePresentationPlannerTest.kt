/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidatePresentationPlannerTest {

    @Test
    fun pendingPunctuationOverridesSmartEnglishCandidates() {
        val raw = paged("raw")
        val smart = page("smart", 4)
        val punctuation = page(".", 2)

        val plan = T9CandidatePresentationPlanner.plan(
            baseInput(
                rawPaged = raw,
                smartEnglishPaged = smart,
                pendingPunctuationPaged = punctuation
            )
        )

        assertSame(punctuation, plan.cursorSource)
        assertTrue(plan.usesPendingPunctuation)
        assertFalse(plan.usesSmartEnglish)
        assertArrayEquals(intArrayOf(2), plan.cursorSource.originalIndices)
    }

    @Test
    fun bulkFilteredChineseCandidatesUseBulkSelectionIndices() {
        val bulk = page("你", 9)

        val plan = T9CandidatePresentationPlanner.plan(
            baseInput(
                chineseT9Active = true,
                bulkFilteredPaged = bulk,
                bulkFilteredMatchedPrefix = "ni",
                bulkFilterPending = false
            )
        )

        assertSame(bulk, plan.candidateSource)
        assertFalse(plan.applyChineseCursor)
        assertTrue(plan.usesBulkSelection)
        assertEquals("ni", plan.matchedPrefix)
        assertArrayEquals(intArrayOf(9), plan.cursorSource.originalIndices)
    }

    @Test
    fun pendingBulkDisplayDoesNotExposeOriginalIndices() {
        val bulk = page("你", 9)

        val plan = T9CandidatePresentationPlanner.plan(
            baseInput(
                chineseT9Active = true,
                bulkFilteredPaged = bulk,
                bulkFilterPending = true
            )
        )

        assertSame(bulk.data, plan.candidateSource.data)
        assertFalse(plan.usesBulkSelection)
        assertArrayEquals(intArrayOf(-1), plan.cursorSource.originalIndices)
    }

    @Test
    fun suppressedChineseCandidatesShowEmptyCursorSource() {
        val plan = T9CandidatePresentationPlanner.plan(
            baseInput(
                chineseT9Active = true,
                suppressEmptyCandidates = true
            )
        )

        assertSame(T9PagedCandidates.Empty, plan.cursorSource)
        assertFalse(plan.applyChineseCursor)
        assertArrayEquals(intArrayOf(), plan.cursorSource.originalIndices)
    }

    private fun baseInput(
        rawPaged: FcitxEvent.PagedCandidateEvent.Data = paged("raw"),
        filteredPaged: T9PagedCandidates = T9PagedCandidates.passthrough(rawPaged),
        smartEnglishPaged: T9PagedCandidates? = null,
        pendingPunctuationPaged: T9PagedCandidates? = null,
        localBudgetedPaged: T9PagedCandidates? = null,
        bulkFilteredPaged: T9PagedCandidates? = null,
        bulkFilteredMatchedPrefix: String? = null,
        bulkFilterPending: Boolean = false,
        chineseT9Active: Boolean = false,
        suppressEmptyCandidates: Boolean = false,
        pendingPinyinSelection: Boolean = false,
        waitForChineseCandidates: Boolean = false
    ): T9CandidatePresentationPlanner.Input =
        T9CandidatePresentationPlanner.Input(
            rawPaged = rawPaged,
            filteredPaged = filteredPaged,
            filteredMatchedPrefix = null,
            smartEnglishPaged = smartEnglishPaged,
            pendingPunctuationPaged = pendingPunctuationPaged,
            localBudgetedPaged = localBudgetedPaged,
            bulkFilteredPaged = bulkFilteredPaged,
            bulkFilteredMatchedPrefix = bulkFilteredMatchedPrefix,
            bulkFilterPending = bulkFilterPending,
            chineseT9Active = chineseT9Active,
            suppressEmptyCandidates = suppressEmptyCandidates,
            pendingPinyinSelection = pendingPinyinSelection,
            waitForChineseCandidates = waitForChineseCandidates
        )

    private fun paged(text: String): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = arrayOf(FcitxEvent.Candidate(label = "", text = text, comment = "")),
            cursorIndex = 0,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )

    private fun page(text: String, originalIndex: Int): T9PagedCandidates =
        T9PagedCandidates(
            data = paged(text),
            originalIndices = intArrayOf(originalIndex)
        )
}

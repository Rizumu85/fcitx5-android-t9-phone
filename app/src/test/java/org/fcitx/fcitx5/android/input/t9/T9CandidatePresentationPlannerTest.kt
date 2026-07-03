/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidatePresentationPlannerTest {

    @Test
    fun pendingPunctuationOverridesSmartEnglishCandidates() {
        val raw = paged("raw")
        val smart = paged("smart")
        val punctuation = paged(".")

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
        assertEquals(T9CandidatePresentationPlanner.OriginalIndexSource.PENDING_PUNCTUATION, plan.originalIndexSource)
    }

    @Test
    fun bulkFilteredChineseCandidatesUseBulkSelectionIndices() {
        val bulk = paged("你")

        val plan = T9CandidatePresentationPlanner.plan(
            baseInput(
                chineseT9Active = true,
                bulkFilteredPaged = bulk,
                bulkFilteredMatchedPrefix = "ni",
                bulkFilterPending = false
            )
        )

        assertSame(bulk, plan.candidateSource)
        assertTrue(plan.applyChineseCursor)
        assertTrue(plan.usesBulkSelection)
        assertEquals("ni", plan.matchedPrefix)
        assertEquals(T9CandidatePresentationPlanner.OriginalIndexSource.BULK_FILTERED, plan.originalIndexSource)
    }

    @Test
    fun pendingBulkDisplayDoesNotExposeOriginalIndices() {
        val bulk = paged("你")

        val plan = T9CandidatePresentationPlanner.plan(
            baseInput(
                chineseT9Active = true,
                bulkFilteredPaged = bulk,
                bulkFilterPending = true
            )
        )

        assertSame(bulk, plan.candidateSource)
        assertFalse(plan.usesBulkSelection)
        assertEquals(T9CandidatePresentationPlanner.OriginalIndexSource.PENDING_BULK_DISPLAY, plan.originalIndexSource)
    }

    @Test
    fun suppressedChineseCandidatesShowEmptyCursorSource() {
        val plan = T9CandidatePresentationPlanner.plan(
            baseInput(
                chineseT9Active = true,
                suppressEmptyCandidates = true
            )
        )

        assertSame(FcitxEvent.PagedCandidateEvent.Data.Empty, plan.cursorSource)
        assertFalse(plan.applyChineseCursor)
        assertEquals(T9CandidatePresentationPlanner.OriginalIndexSource.PAGED, plan.originalIndexSource)
    }

    private fun baseInput(
        rawPaged: FcitxEvent.PagedCandidateEvent.Data = paged("raw"),
        filteredPaged: FcitxEvent.PagedCandidateEvent.Data = rawPaged,
        smartEnglishPaged: FcitxEvent.PagedCandidateEvent.Data? = null,
        pendingPunctuationPaged: FcitxEvent.PagedCandidateEvent.Data? = null,
        localBudgetedPaged: FcitxEvent.PagedCandidateEvent.Data? = null,
        bulkFilteredPaged: FcitxEvent.PagedCandidateEvent.Data? = null,
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
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateUiSnapshotPipelineTest {

    @Test
    fun smartEnglishSnapshotOwnsOriginalIndexMapping() {
        val pipeline = pipeline()
        val shown = requireNotNull(
            pipeline.buildSmartEnglishPaged(
                smartEnglishSnapshot(paged("hello", "help", cursor = 1))
            )
        )

        pipeline.updateShownState(
            source = T9CandidateUiSnapshotPipeline.ShownSource.SMART_ENGLISH,
            paged = shown.data,
            originalIndices = shown.originalIndices,
            matchedPrefix = null
        )

        assertTrue(pipeline.hasCurrentBottomCandidateRow)
        assertEquals(1, pipeline.smartEnglishShortcutOriginalIndex(1))
        assertNull(pipeline.pendingPunctuationShortcutOriginalIndex(1))
    }

    @Test
    fun invalidatedShownInteractionCannotCommitRetainedCandidate() {
        val pipeline = pipeline()
        pipeline.updateShownState(
            source = T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_LOCAL,
            paged = paged("你", cursor = 0),
            originalIndices = intArrayOf(0),
            matchedPrefix = null
        )

        pipeline.invalidateShownInteraction()

        assertFalse(pipeline.hasCurrentBottomCandidateRow)
        assertNull(pipeline.commitCurrentBottomCandidate())
    }

    @Test
    fun chineseSelectionTicketIsBoundToShownSourceAndOriginalIndex() {
        val pipeline = pipeline()
        val shown = paged("你", "好", cursor = 0)
        pipeline.updateShownState(
            source = T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_LOCAL,
            paged = shown,
            originalIndices = intArrayOf(3, 7),
            matchedPrefix = null
        )

        val ticket = pipeline.currentChineseSelectionTicket(7, shown.candidates[1])
        pipeline.updateShownState(
            source = T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_ENGINE,
            paged = shown,
            originalIndices = intArrayOf(3, 7),
            matchedPrefix = null
        )

        assertEquals(1, ticket?.shownIndex)
        assertEquals(7, ticket?.originalIndex)
        assertFalse(pipeline.currentChineseSelectionTicket(7, shown.candidates[1]) == ticket)
    }

    @Test
    fun smartEnglishPageOffsetReturnsNextOriginalIndex() {
        val pipeline = pipeline(characterBudget = 30)
        val shown = requireNotNull(
            pipeline.buildSmartEnglishPaged(
                smartEnglishSnapshot(
                    paged(
                        "a", "b", "c", "d", "e",
                        "f", "g", "h", "i", "j", "k",
                        cursor = 0
                    )
                )
            )
        )
        pipeline.updateShownState(
            source = T9CandidateUiSnapshotPipeline.ShownSource.SMART_ENGLISH,
            paged = shown.data,
            originalIndices = shown.originalIndices,
            matchedPrefix = null
        )

        val offset = pipeline.offsetCurrentPage(1)

        assertEquals(
            T9CandidateUiSnapshotPipeline.PageOffset.SmartEnglish(nextOriginalIndex = 10),
            offset
        )
    }

    @Test
    fun pendingPunctuationMoveUpdatesShownCursorAndPreviewIndex() {
        val pipeline = pipeline()
        val shown = pipeline.buildPendingPunctuationPaged(paged(",", ".", "?", cursor = 0))
        pipeline.updateShownState(
            source = T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION,
            paged = shown.data,
            originalIndices = shown.originalIndices,
            matchedPrefix = null
        )

        val moved = pipeline.moveCurrentBottomCandidate(1)

        require(moved is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.LocalSelection)
        assertEquals(T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION, moved.source)
        assertEquals(1, moved.originalIndex)
        assertEquals(1, pipeline.currentShownSnapshot?.paged?.cursorIndex)
        assertArrayEquals(intArrayOf(0, 1, 2), pipeline.currentShownSnapshot?.originalIndices)
    }

    @Test
    fun pendingPunctuationCommitUsesCurrentOriginalIndex() {
        val pipeline = pipeline()
        val shown = pipeline.buildPendingPunctuationPaged(paged(",", ".", "?", cursor = 2))
        pipeline.updateShownState(
            source = T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION,
            paged = shown.data,
            originalIndices = shown.originalIndices,
            matchedPrefix = null
        )

        val commit = pipeline.commitCurrentBottomCandidate()

        assertEquals(
            T9CandidateUiSnapshotPipeline.CommitBottomCandidate.PendingPunctuation(originalIndex = 2),
            commit
        )
    }

    @Test
    fun chineseLocalBudgetPagingLivesInSnapshotPipeline() {
        val pipeline = pipeline(characterBudget = 4)
        val data = paged("一二三", "四五", "六", cursor = 0)

        val source = T9PagedCandidates.passthrough(data)
        val first = pipeline.buildChineseLocalBudgetedPagedFromCurrentPage(source)
        val moved = pipeline.offsetChineseLocalBudgetedPage(1)
        val second = pipeline.buildChineseLocalBudgetedPagedFromCurrentPage(source)

        require(first != null)
        require(second != null)
        assertTrue(moved)
        assertEquals(listOf("一二三"), first.data.candidates.map { it.text })
        assertEquals(listOf("四五", "六"), second.data.candidates.map { it.text })
        assertArrayEquals(intArrayOf(1, 2), second.originalIndices)
    }

    @Test
    fun chineseHanziCursorLivesInSnapshotPipeline() {
        val pipeline = pipeline()
        val data = paged("你", "呢", "泥", cursor = 0)
        val signature = pipeline.buildChineseCursorContextSignature("ni", listOf("ni"))

        val initial = pipeline.applyChineseHanziCursor(data, signature)
        val moved = pipeline.moveChineseHanziCursor(initial, 2)
        val reapplied = pipeline.applyChineseHanziCursor(
            data = moved ?: data,
            cursorContextSignature = signature
        )

        assertEquals(2, moved?.cursorIndex)
        assertEquals(2, reapplied.cursorIndex)
    }

    @Test
    fun chineseBulkSelectionOwnsPagingMoveAndCommit() {
        val pipeline = T9CandidateUiSnapshotPipeline(
            characterBudget = { 4 },
            widthBudget = { null },
            candidateMatchesPrefix = { candidate, prefix -> candidate.comment.startsWith(prefix) }
        )
        val signature = pipeline.chineseBulkFilterRequestSignature(
            prefixes = listOf("ni"),
            preedit = "ni",
            candidates = emptyArray()
        )
        pipeline.startChineseBulkFilterRequest(listOf("ni"), signature)

        val state = pipeline.finishChineseBulkFilterRequest(
            signature = signature,
            rawCandidates = listOf("你 ni", "泥 ni", "逆 ni", "拟 ni", "年 nian"),
            prefixes = listOf("ni"),
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal
        )

        require(state?.paged != null)
        pipeline.updateShownState(
            source = T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_BULK,
            paged = state.paged.data,
            originalIndices = state.paged.originalIndices,
            matchedPrefix = state.matchedPrefix
        )

        val moved = pipeline.moveCurrentBottomCandidate(1)
        require(moved is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.LocalSelection)
        assertEquals(T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_BULK, moved.source)
        assertEquals(1, pipeline.currentShownSnapshot?.paged?.cursorIndex)

        val refreshed = pipeline.chineseBulkFilterState.paged
        require(refreshed != null)
        assertEquals(1, refreshed.data.cursorIndex)

        val shortcutCommit = pipeline.commitBottomCandidateAt(2)
        require(shortcutCommit is T9CandidateUiSnapshotPipeline.CommitBottomCandidate.Chinese)
        assertEquals(2, shortcutCommit.originalIndex)
        assertEquals("逆", shortcutCommit.candidate.text)
        assertEquals("ni", shortcutCommit.matchedPrefix)
        assertTrue(shortcutCommit.fromAllCandidates)

        val page = pipeline.offsetCurrentPage(1)
        require(page is T9CandidateUiSnapshotPipeline.PageOffset.Refresh)
        assertEquals(listOf("年"), pipeline.currentShownSnapshot?.paged?.candidates?.map { it.text })
        assertArrayEquals(intArrayOf(4), pipeline.currentShownSnapshot?.originalIndices)
    }

    @Test
    fun pinyinWindowStateLivesInSnapshotPipeline() {
        val pipeline = pipeline()

        val initial = pipeline.submitPinyinWindow(listOf("gei", "hei", "ge", "he", "g", "h", "i"))
        val moved = pipeline.movePinyinWindow(2)
        val highlighted = pipeline.highlightedPinyin()
        val reset = pipeline.resetPinyinHighlight()

        assertEquals(listOf("gei", "hei", "ge", "he", "g", "h", "i"), initial.items)
        assertEquals(2, moved?.highlightedIndex)
        assertEquals("ge", highlighted)
        assertEquals(0, reset?.highlightedIndex)
    }

    @Test
    fun resetClearsChineseAndPinyinState() {
        val pipeline = pipeline(characterBudget = 4)
        pipeline.buildChineseLocalBudgetedPagedFromCurrentPage(
            T9PagedCandidates.passthrough(paged("一二三", "四五", "六", cursor = 0))
        )
        assertTrue(pipeline.offsetChineseLocalBudgetedPage(1))
        pipeline.submitPinyinWindow(listOf("ge", "he"))

        pipeline.reset()

        assertFalse(pipeline.offsetChineseLocalBudgetedPage(1))
        assertNull(pipeline.currentPinyinWindowState())
    }

    private fun pipeline(characterBudget: Int = 20): T9CandidateUiSnapshotPipeline =
        T9CandidateUiSnapshotPipeline(
            characterBudget = { characterBudget },
            widthBudget = { null }
        )

    private fun paged(
        vararg words: String,
        cursor: Int
    ): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = words.map {
                FcitxEvent.Candidate(label = "", text = it, comment = "")
            }.toTypedArray(),
            cursorIndex = cursor,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )

    private fun smartEnglishSnapshot(
        paged: FcitxEvent.PagedCandidateEvent.Data
    ): SmartEnglishUiSnapshot = SmartEnglishUiSnapshot(
        publicationKey = "publication",
        contentKey = "content",
        paged = paged,
        presentation = null
    )
}

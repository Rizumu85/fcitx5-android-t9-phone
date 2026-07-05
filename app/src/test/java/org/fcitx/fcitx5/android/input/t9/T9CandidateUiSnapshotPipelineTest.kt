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
        val shown = pipeline.buildSmartEnglishPaged(paged("hello", "help", cursor = 1))

        pipeline.updateShownState(
            paged = shown.data,
            originalIndices = shown.originalIndices,
            usesSmartEnglish = true,
            usesPendingPunctuation = false
        )

        assertTrue(pipeline.ownsCurrentShownState)
        assertEquals(1, pipeline.smartEnglishShortcutOriginalIndex(1))
        assertNull(pipeline.pendingPunctuationShortcutOriginalIndex(1))
    }

    @Test
    fun smartEnglishPageOffsetReturnsNextOriginalIndex() {
        val pipeline = pipeline(characterBudget = 30)
        val shown = pipeline.buildSmartEnglishPaged(
            paged(
                "a", "b", "c", "d", "e",
                "f", "g", "h", "i", "j", "k",
                cursor = 0
            )
        )
        pipeline.updateShownState(
            paged = shown.data,
            originalIndices = shown.originalIndices,
            usesSmartEnglish = true,
            usesPendingPunctuation = false
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
            paged = shown.data,
            originalIndices = shown.originalIndices,
            usesSmartEnglish = false,
            usesPendingPunctuation = true
        )

        val moved = pipeline.moveCurrentBottomCandidate(1)

        require(moved is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.PendingPunctuation)
        assertEquals(1, moved.previewOriginalIndex)
        assertEquals(1, moved.shown.data.cursorIndex)
        assertArrayEquals(intArrayOf(0, 1, 2), moved.shown.originalIndices)
    }

    @Test
    fun pendingPunctuationCommitUsesCurrentOriginalIndex() {
        val pipeline = pipeline()
        val shown = pipeline.buildPendingPunctuationPaged(paged(",", ".", "?", cursor = 2))
        pipeline.updateShownState(
            paged = shown.data,
            originalIndices = shown.originalIndices,
            usesSmartEnglish = false,
            usesPendingPunctuation = true
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

        val first = pipeline.buildChineseLocalBudgetedPagedFromCurrentPage(data)
        val moved = pipeline.offsetChineseLocalBudgetedPage(1)
        val second = pipeline.buildChineseLocalBudgetedPagedFromCurrentPage(data)

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
        pipeline.buildChineseLocalBudgetedPagedFromCurrentPage(paged("一二三", "四五", "六", cursor = 0))
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
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateInteractionControllerTest {

    @Test
    fun smartEnglishMoveUsesOriginalIndexFromShownPage() {
        val pipeline = pipelineWithShown(
            source = T9CandidateUiSnapshotPipeline.ShownSource.SMART_ENGLISH,
            originalIndices = intArrayOf(10, 11),
            paged = paged(cursor = 0, "hello", "help")
        )
        val host = FakeHost()

        val handled = T9CandidateInteractionController(pipeline, host).moveBottomCandidate(1)

        assertEquals(true, handled)
        assertEquals(listOf(11), host.smartEnglishSelections)
        assertEquals(1, host.localSelectionPublishCount)
        assertEquals(0, host.refreshCount)
    }

    @Test
    fun pendingPunctuationMoveAppliesShownPagePreviewAndRefreshes() {
        val pipeline = pipelineWithShown(
            source = T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION,
            originalIndices = intArrayOf(20, 21),
            paged = paged(cursor = 0, ".", ",")
        )
        val host = FakeHost()

        val handled = T9CandidateInteractionController(pipeline, host).moveBottomCandidate(1)

        assertEquals(true, handled)
        assertEquals(listOf(21), host.punctuationPreviews)
        assertEquals(1, host.localSelectionPublishCount)
        assertEquals(0, host.refreshCount)
    }

    @Test
    fun chineseBulkCommitDelegatesBulkSelectionWithMatchedPrefix() {
        val pipeline = pipelineWithShown(
            source = T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_BULK,
            originalIndices = intArrayOf(30),
            matchedPrefix = "ge",
            paged = paged(cursor = 0, "给")
        )
        val host = FakeHost()

        val handled = T9CandidateInteractionController(pipeline, host).commitBottomCandidate()

        assertEquals(true, handled)
        assertEquals(listOf(FakeHost.ChineseSelection(30, "给", "ge", true)), host.chineseSelections)
    }

    @Test
    fun pageMoveMutatesSourceThenPublishesCompleteRefresh() {
        val pipeline = pipelineWithShown(
            source = T9CandidateUiSnapshotPipeline.ShownSource.SMART_ENGLISH,
            originalIndices = intArrayOf(0),
            paged = paged(cursor = 0, "a").copy(hasNext = true)
        )
        pipeline.buildSmartEnglishPaged(
            SmartEnglishUiSnapshot(
                publicationKey = "publication",
                contentKey = "content",
                paged = FcitxEvent.PagedCandidateEvent.Data(
                    candidates = (0..10).map {
                        FcitxEvent.Candidate(label = "", text = "word$it", comment = "")
                    }.toTypedArray(),
                    cursorIndex = 0,
                    layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
                    hasPrev = false,
                    hasNext = false
                ),
                presentation = null
            )
        )
        val host = FakeHost()

        val handled = T9CandidateInteractionController(pipeline, host).offsetBottomCandidatePage(1)

        assertEquals(true, handled)
        assertEquals(1, host.refreshCount)
        assertEquals(0, host.localSelectionPublishCount)
    }

    @Test
    fun chinesePunctuationFollowUpRunsAfterCandidateSelection() {
        val pipeline = pipelineWithShown(
            source = T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_ENGINE,
            originalIndices = intArrayOf(4),
            paged = paged(cursor = 0, "好")
        )
        val host = FakeHost()
        var followedUp = false

        val handled = T9CandidateInteractionController(pipeline, host)
            .commitCurrentChineseCandidate { followedUp = true }

        assertEquals(true, handled)
        assertTrue(followedUp)
        assertEquals(listOf(FakeHost.ChineseSelection(4, "好", null, false)), host.chineseSelections)
    }

    @Test
    fun nonOwnedShownStateReturnsNullForFallbackPath() {
        val pipeline = pipelineWithShown(
            source = T9CandidateUiSnapshotPipeline.ShownSource.OTHER,
            originalIndices = intArrayOf(0),
            paged = paged(cursor = 0, "你")
        )
        val host = FakeHost()

        val handled = T9CandidateInteractionController(pipeline, host).commitBottomCandidate()

        assertNull(handled)
    }

    private fun pipelineWithShown(
        source: T9CandidateUiSnapshotPipeline.ShownSource,
        originalIndices: IntArray,
        paged: FcitxEvent.PagedCandidateEvent.Data,
        matchedPrefix: String? = null
    ): T9CandidateUiSnapshotPipeline =
        T9CandidateUiSnapshotPipeline(
            characterBudget = { 10 },
            widthBudget = { null }
        ).also {
            it.updateShownState(
                source = source,
                paged = paged,
                originalIndices = originalIndices,
                matchedPrefix = matchedPrefix
            )
        }

    private fun paged(
        cursor: Int,
        vararg words: String
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

    private class FakeHost : T9CandidateInteractionController.Host {
        data class ChineseSelection(
            val originalIndex: Int,
            val text: String,
            val matchedPrefix: String?,
            val fromAllCandidates: Boolean
        )

        val smartEnglishSelections = mutableListOf<Int>()
        val smartEnglishCommits = mutableListOf<Int>()
        val punctuationCommits = mutableListOf<Int>()
        val punctuationPreviews = mutableListOf<Int>()
        val chineseSelections = mutableListOf<ChineseSelection>()
        val enginePageOffsets = mutableListOf<Int>()
        var refreshCount = 0
        var localSelectionPublishCount = 0

        override fun moveSmartEnglishSelection(originalIndex: Int): Boolean {
            smartEnglishSelections += originalIndex
            return true
        }

        override fun commitSmartEnglishCandidate(originalIndex: Int): Boolean {
            smartEnglishCommits += originalIndex
            return true
        }

        override fun commitPendingPunctuationCandidate(originalIndex: Int): Boolean {
            punctuationCommits += originalIndex
            return true
        }

        override fun movePendingPunctuationSelection(originalIndex: Int): Boolean {
            punctuationPreviews += originalIndex
            return true
        }

        override fun publishLocalSelection() {
            localSelectionPublishCount += 1
        }

        override fun refreshT9Ui() {
            refreshCount += 1
        }

        override fun offsetEngineCandidatePage(delta: Int): Boolean {
            enginePageOffsets += delta
            return true
        }

        override fun selectChineseCandidate(
            originalIndex: Int,
            selectedCandidate: FcitxEvent.Candidate,
            matchedPrefix: String?,
            fromAllCandidates: Boolean,
            onSelected: (() -> Unit)?
        ): Boolean {
            chineseSelections += ChineseSelection(
                originalIndex,
                selectedCandidate.text,
                matchedPrefix,
                fromAllCandidates
            )
            onSelected?.invoke()
            return true
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.input.handwriting.HandwritingUiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateSourceSessionsTest {

    @Test
    fun ownedShownRowOriginalIndicesLiveWithSourceSessions() {
        val sessions = sessions()
        val shown = sessions.buildPendingPunctuationPaged(paged(cursor = 0, ",", ".", "?"))

        sessions.updateShownState(
            source = T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION,
            paged = shown.data,
            originalIndices = shown.originalIndices,
            matchedPrefix = null
        )

        assertTrue(sessions.hasCurrentBottomCandidateRow)
        assertEquals(1, sessions.pendingPunctuationShortcutOriginalIndex(1))
        assertNull(sessions.smartEnglishShortcutOriginalIndex(1))
    }

    @Test
    fun pendingPunctuationMoveUpdatesShownSession() {
        val sessions = sessions()
        val shown = sessions.buildPendingPunctuationPaged(paged(cursor = 0, ",", ".", "?"))
        sessions.updateShownState(
            source = T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION,
            paged = shown.data,
            originalIndices = shown.originalIndices,
            matchedPrefix = null
        )

        val moved = sessions.moveCurrentBottomCandidate(2)

        require(moved is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.LocalSelection)
        assertEquals(T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION, moved.source)
        assertEquals(2, moved.originalIndex)
        assertEquals(2, sessions.currentShownSnapshot?.paged?.cursorIndex)
        assertEquals(
            T9CandidateUiSnapshotPipeline.CommitBottomCandidate.PendingPunctuation(2),
            sessions.commitCurrentBottomCandidate()
        )
    }

    @Test
    fun handwritingPagingKeepsOriginalCandidateIndex() {
        val sessions = sessions(characterBudget = 4)
        val first = requireNotNull(
            sessions.buildHandwritingPaged(
                HandwritingUiSnapshot(
                    revision = 1,
                    candidates = listOf("你", "好", "世", "界", "中", "文"),
                    selectedIndex = 0
                )
            )
        )
        sessions.updateShownState(
            source = T9CandidateUiSnapshotPipeline.ShownSource.HANDWRITING,
            paged = first.data,
            originalIndices = first.originalIndices,
            matchedPrefix = null
        )

        val page = sessions.offsetCurrentPage(1)

        require(page is T9CandidateUiSnapshotPipeline.PageOffset.Handwriting)
        assertEquals(4, page.nextOriginalIndex)
    }

    @Test
    fun handwritingSelectionRevisionKeepsCandidateContentKeyStable() {
        val candidates = listOf("你", "好", "世", "界")

        val beforeMove = HandwritingUiSnapshot(1, candidates, selectedIndex = 0)
        val afterMove = HandwritingUiSnapshot(2, candidates, selectedIndex = 2)

        assertEquals(beforeMove.contentKey, afterMove.contentKey)
    }

    private fun sessions(characterBudget: Int = 20): T9CandidateSourceSessions =
        T9CandidateSourceSessions(
            characterBudget = { characterBudget },
            widthBudget = { null },
            candidateMatchesPrefix = { _, _ -> false }
        )

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
}

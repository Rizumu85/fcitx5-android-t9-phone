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

        require(moved is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.PendingPunctuation)
        assertEquals(2, moved.previewOriginalIndex)
        assertEquals(2, sessions.currentShownSnapshot?.paged?.cursorIndex)
        assertEquals(
            T9CandidateUiSnapshotPipeline.CommitBottomCandidate.PendingPunctuation(2),
            sessions.commitCurrentBottomCandidate()
        )
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

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class T9CandidateSnapshotTest {

    @Test
    fun pagerSnapshotUsesCandidateContentEquality() {
        val first = T9CandidateSnapshots.pagerSnapshot(
            paged(candidate("你", "ni"), candidate("好", "hao")),
            characterBudget = 20
        )
        val second = T9CandidateSnapshots.pagerSnapshot(
            paged(candidate("你", "ni"), candidate("好", "hao")),
            characterBudget = 20
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun pagerSnapshotChangesWhenBudgetOrCandidateChanges() {
        val baseline = T9CandidateSnapshots.pagerSnapshot(
            paged(candidate("你", "ni")),
            characterBudget = 20
        )
        val differentBudget = T9CandidateSnapshots.pagerSnapshot(
            paged(candidate("你", "ni")),
            characterBudget = 8
        )
        val differentCandidate = T9CandidateSnapshots.pagerSnapshot(
            paged(candidate("呢", "ne")),
            characterBudget = 20
        )

        assertNotEquals(baseline, differentBudget)
        assertNotEquals(baseline, differentCandidate)
    }

    private fun paged(
        vararg candidates: FcitxEvent.Candidate
    ): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = candidates.toList().toTypedArray(),
            cursorIndex = 0,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )

    private fun candidate(
        text: String,
        comment: String
    ): FcitxEvent.Candidate =
        FcitxEvent.Candidate(label = "", text = text, comment = comment)
}

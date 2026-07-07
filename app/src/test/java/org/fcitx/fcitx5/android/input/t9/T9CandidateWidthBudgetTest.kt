/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class T9CandidateWidthBudgetTest {

    @Test
    fun activeScaleOnlyAppliesToActiveCandidate() {
        val budget = T9CandidateWidthBudget(
            maxWidthPx = 500,
            candidateSpacingPx = 2,
            candidateHorizontalPaddingPx = 1,
            minimumCandidateWidthPx = 1,
            activeScalePercent = 150,
            measureTextWidthPx = { it.length * 10 }
        )
        val candidate = FcitxEvent.Candidate(label = "", text = "abc", comment = "")

        assertEquals(50, budget.candidateWidthPx(candidate, active = true))
        assertEquals(34, budget.candidateWidthPx(candidate, active = false))
    }

    @Test
    fun measuredCandidateWidthCanReplacePaintEstimate() {
        val budget = T9CandidateWidthBudget.measuredCandidates(
            maxWidthPx = 500,
            candidateSpacingPx = 2,
            minimumCandidateWidthPx = 1,
            activeScalePercent = 150,
            measurementSignature = "test",
            measureCandidateWidthPx = { candidate, _ -> candidate.text.length * 7 }
        )
        val candidate = FcitxEvent.Candidate(label = "", text = "abcd", comment = "")

        assertEquals(44, budget.candidateWidthPx(candidate, active = true))
        assertEquals(30, budget.candidateWidthPx(candidate, active = false))
    }
}

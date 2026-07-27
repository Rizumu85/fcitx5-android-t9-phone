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
    fun rowWidthUsesOneStableFocusEnvelope() {
        val budget = T9CandidateWidthBudget(
            maxWidthPx = 500,
            candidateSpacingPx = 2,
            candidateHorizontalPaddingPx = 1,
            minimumCandidateWidthPx = 1,
            rowHorizontalPaddingPx = 3,
            activeScalePercent = 150,
            measureTextWidthPx = { it.length * 10 }
        )
        val candidates = listOf(
            FcitxEvent.Candidate(label = "", text = "abc", comment = ""),
            FcitxEvent.Candidate(label = "", text = "de", comment = "")
        )

        assertEquals(78, budget.rowWidthPx(candidates))
    }

}

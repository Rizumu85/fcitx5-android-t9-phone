/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateBudgetTest {

    @Test
    fun englishWordCostScalesWithLength() {
        assertEquals(1, T9CandidateBudget.candidateCost("I"))
        assertEquals(2, T9CandidateBudget.candidateCost("home"))
        assertEquals(2, T9CandidateBudget.candidateCost("hello"))
        assertEquals(3, T9CandidateBudget.candidateCost("candidate"))
        assertEquals(4, T9CandidateBudget.candidateCost("internationalization"))
        assertTrue(T9CandidateBudget.candidateCost("internationalization") < 8)
    }
}

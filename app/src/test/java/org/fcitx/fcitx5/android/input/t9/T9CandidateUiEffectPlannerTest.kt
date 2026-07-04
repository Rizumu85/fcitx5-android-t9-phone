/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateUiEffectPlannerTest {

    @Test
    fun normalChineseCompositionRequestsBulkCandidates() {
        assertEquals(
            T9CandidateUiEffectPlanner.BulkEffect.REQUEST,
            T9CandidateUiEffectPlanner.bulkEffect(
                isChineseSurface = true,
                suppressEmptyCandidates = false,
                pendingPinyinSelection = false,
                waitForChineseCandidates = false,
                hasPendingPunctuation = false
            )
        )
    }

    @Test
    fun nonChineseOrTransientChineseStateResetsBulkCandidates() {
        val reset = T9CandidateUiEffectPlanner.BulkEffect.RESET
        assertEquals(reset, bulk(isChineseSurface = false))
        assertEquals(reset, bulk(suppressEmptyCandidates = true))
        assertEquals(reset, bulk(pendingPinyinSelection = true))
        assertEquals(reset, bulk(waitForChineseCandidates = true))
        assertEquals(reset, bulk(hasPendingPunctuation = true))
    }

    @Test
    fun localBudgetOnlyBuildsForUnfilteredStableChinesePage() {
        val build = T9CandidateUiEffectPlanner.localBudgetDecision(
            suppressEmptyCandidates = false,
            pendingPinyinSelection = false,
            hasPendingPunctuation = false,
            chineseT9Active = true,
            hasFilterPrefixes = false,
            hasBulkFilteredPage = false,
            waitForChineseCandidates = false
        )
        val reset = T9CandidateUiEffectPlanner.localBudgetDecision(
            suppressEmptyCandidates = false,
            pendingPinyinSelection = false,
            hasPendingPunctuation = false,
            chineseT9Active = true,
            hasFilterPrefixes = true,
            hasBulkFilteredPage = false,
            waitForChineseCandidates = false
        )

        assertTrue(build.buildLocalBudgetPage)
        assertFalse(build.resetLocalBudget)
        assertFalse(reset.buildLocalBudgetPage)
        assertTrue(reset.resetLocalBudget)
    }

    private fun bulk(
        isChineseSurface: Boolean = true,
        suppressEmptyCandidates: Boolean = false,
        pendingPinyinSelection: Boolean = false,
        waitForChineseCandidates: Boolean = false,
        hasPendingPunctuation: Boolean = false
    ): T9CandidateUiEffectPlanner.BulkEffect =
        T9CandidateUiEffectPlanner.bulkEffect(
            isChineseSurface = isChineseSurface,
            suppressEmptyCandidates = suppressEmptyCandidates,
            pendingPinyinSelection = pendingPinyinSelection,
            waitForChineseCandidates = waitForChineseCandidates,
            hasPendingPunctuation = hasPendingPunctuation
        )
}

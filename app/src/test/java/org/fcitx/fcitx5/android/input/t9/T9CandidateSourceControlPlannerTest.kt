/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateSourceControlPlannerTest {

    @Test
    fun chineseInputTakesPriorityOverSmartEnglish() {
        assertEquals(
            T9CandidateSourceControlPlanner.Surface.CHINESE,
            T9CandidateSourceControlPlanner.surface(
                chineseActive = true,
                smartEnglishActive = true
            )
        )
    }

    @Test
    fun inactiveInputsUseOtherSurface() {
        assertEquals(
            T9CandidateSourceControlPlanner.Surface.OTHER,
            T9CandidateSourceControlPlanner.surface(
                chineseActive = false,
                smartEnglishActive = false
            )
        )
    }

    @Test
    fun waitingChineseCandidatesDefersRenderAndClearsShownCandidates() {
        val loadingState = ChineseT9CandidateLoadingState().apply {
            startIfNeeded(
                chineseT9Active = true,
                ticket = ChineseT9CompositionTicket(
                    scheme = ChineseT9Scheme.PINYIN,
                    rawSequence = "2",
                    digitSequence = "2",
                    sessionRevision = 1
                )
            )
        }

        val plan = T9CandidateSourceControlPlanner.plan(
            input(
                loadingState = loadingState,
                rawCandidatesEmpty = true,
                compositionKeyCount = 1
            )
        )

        assertTrue(plan.waitForChineseCandidates)
        assertTrue(plan.deferRender)
        assertEquals(T9CandidateSourceControlPlanner.BulkAction.RESET, plan.bulkAction)
        assertEquals(T9CandidateSourceControlPlanner.FilterAction.EMPTY, plan.filterAction)
        assertFalse(plan.shouldBuildLocalBudget(hasBulkFilteredPage = false, bulkFilterPending = false))
    }

    @Test
    fun emptyChineseCompositionSuppressesCandidatesAndResetsBulk() {
        val plan = T9CandidateSourceControlPlanner.plan(
            input(compositionKeyCount = 0)
        )

        assertTrue(plan.suppressEmptyCandidates)
        assertFalse(plan.deferRender)
        assertEquals(T9CandidateSourceControlPlanner.BulkAction.RESET, plan.bulkAction)
        assertEquals(T9CandidateSourceControlPlanner.FilterAction.EMPTY, plan.filterAction)
    }

    @Test
    fun ordinaryPinyinUsesAcceptedEnginePageWithoutUnfilteredBulkReload() {
        val plan = T9CandidateSourceControlPlanner.plan(
            input(compositionKeyCount = 1)
        )

        assertEquals(T9CandidateSourceControlPlanner.BulkAction.RESET, plan.bulkAction)
        assertEquals(T9CandidateSourceControlPlanner.FilterAction.CHINESE_READING_FILTER, plan.filterAction)
        assertTrue(plan.shouldBuildLocalBudget(hasBulkFilteredPage = false, bulkFilterPending = false))
        assertFalse(plan.shouldBuildLocalBudget(hasBulkFilteredPage = true, bulkFilterPending = false))
        assertFalse(plan.shouldBuildLocalBudget(hasBulkFilteredPage = false, bulkFilterPending = true))
    }

    @Test
    fun resolvedPinyinPrefixRequestsCrossPageBulkFilter() {
        val plan = T9CandidateSourceControlPlanner.plan(
            input(
                compositionKeyCount = 2,
                filterPrefixesEmpty = false
            )
        )

        assertEquals(T9CandidateSourceControlPlanner.BulkAction.REQUEST, plan.bulkAction)
        assertEquals(
            T9CandidateSourceControlPlanner.FilterAction.CHINESE_READING_FILTER,
            plan.filterAction
        )
        assertFalse(plan.shouldBuildLocalBudget(hasBulkFilteredPage = false, bulkFilterPending = false))
    }

    @Test
    fun pendingPunctuationResetsBulkButKeepsCandidatesVisible() {
        val plan = T9CandidateSourceControlPlanner.plan(
            input(
                pendingPunctuationActive = true,
                compositionKeyCount = 1
            )
        )

        assertFalse(plan.waitForChineseCandidates)
        assertFalse(plan.suppressEmptyCandidates)
        assertEquals(T9CandidateSourceControlPlanner.BulkAction.RESET, plan.bulkAction)
        assertEquals(T9CandidateSourceControlPlanner.FilterAction.CHINESE_READING_FILTER, plan.filterAction)
        assertFalse(plan.shouldBuildLocalBudget(hasBulkFilteredPage = false, bulkFilterPending = false))
    }

    @Test
    fun rawCodeSchemeBypassesPinyinBulkFilteringAndUsesCurrentPageBudget() {
        val plan = T9CandidateSourceControlPlanner.plan(
            input(
                compositionKeyCount = 2,
                chineseScheme = ChineseT9Scheme.STROKE
            )
        )

        assertEquals(T9CandidateSourceControlPlanner.BulkAction.RESET, plan.bulkAction)
        assertEquals(T9CandidateSourceControlPlanner.FilterAction.PASSTHROUGH, plan.filterAction)
        assertTrue(plan.shouldBuildLocalBudget(hasBulkFilteredPage = false, bulkFilterPending = false))
    }

    @Test
    fun unresolvedZhuyinWaitsForTheCoherentRimeCandidateFrame() {
        val loadingState = ChineseT9CandidateLoadingState().apply {
            startIfNeeded(
                chineseT9Active = true,
                ticket = ChineseT9CompositionTicket(
                    scheme = ChineseT9Scheme.ZHUYIN,
                    rawSequence = "3",
                    digitSequence = "3",
                    sessionRevision = 1
                )
            )
        }

        val plan = T9CandidateSourceControlPlanner.plan(
            input(
                loadingState = loadingState,
                rawCandidatesEmpty = true,
                compositionKeyCount = 1,
                chineseScheme = ChineseT9Scheme.ZHUYIN
            )
        )

        assertTrue(plan.waitForChineseCandidates)
        assertTrue(plan.deferRender)
        assertEquals(T9CandidateSourceControlPlanner.BulkAction.RESET, plan.bulkAction)
        assertEquals(T9CandidateSourceControlPlanner.FilterAction.EMPTY, plan.filterAction)
    }

    @Test
    fun readyZhuyinUsesTheEnginePageWithoutAParallelReadingFilter() {
        val plan = T9CandidateSourceControlPlanner.plan(
            input(
                compositionKeyCount = 2,
                chineseScheme = ChineseT9Scheme.ZHUYIN
            )
        )

        assertEquals(T9CandidateSourceControlPlanner.BulkAction.RESET, plan.bulkAction)
        assertEquals(T9CandidateSourceControlPlanner.FilterAction.PASSTHROUGH, plan.filterAction)
        assertTrue(plan.shouldBuildLocalBudget(hasBulkFilteredPage = false, bulkFilterPending = false))
    }

    @Test
    fun selectedZhuyinReadingUsesTheSharedCrossPageFilter() {
        val plan = T9CandidateSourceControlPlanner.plan(
            input(
                compositionKeyCount = 2,
                filterPrefixesEmpty = false,
                chineseScheme = ChineseT9Scheme.ZHUYIN
            )
        )

        assertEquals(T9CandidateSourceControlPlanner.BulkAction.REQUEST, plan.bulkAction)
        assertEquals(
            T9CandidateSourceControlPlanner.FilterAction.CHINESE_READING_FILTER,
            plan.filterAction
        )
        assertFalse(plan.shouldBuildLocalBudget(hasBulkFilteredPage = false, bulkFilterPending = false))
    }

    @Test
    fun invalidZhuyinShowsNoMatchWithoutEngineOrCandidateWork() {
        val plan = T9CandidateSourceControlPlanner.plan(
            input(
                compositionKeyCount = 2,
                chineseScheme = ChineseT9Scheme.ZHUYIN,
                invalidReading = true
            )
        )

        assertFalse(plan.waitForChineseCandidates)
        assertFalse(plan.deferRender)
        assertEquals(T9CandidateSourceControlPlanner.BulkAction.RESET, plan.bulkAction)
        assertEquals(T9CandidateSourceControlPlanner.FilterAction.EMPTY, plan.filterAction)
        assertFalse(plan.shouldBuildLocalBudget(hasBulkFilteredPage = false, bulkFilterPending = false))
    }

    @Test
    fun smartEnglishUsesPassthroughCandidatesAndNoLocalBudget() {
        val plan = T9CandidateSourceControlPlanner.plan(
            input(surface = T9CandidateSourceControlPlanner.Surface.SMART_ENGLISH)
        )

        assertEquals(T9CandidateSourceControlPlanner.BulkAction.RESET, plan.bulkAction)
        assertEquals(T9CandidateSourceControlPlanner.FilterAction.PASSTHROUGH, plan.filterAction)
        assertFalse(plan.shouldBuildLocalBudget(hasBulkFilteredPage = false, bulkFilterPending = false))
    }

    private fun input(
        surface: T9CandidateSourceControlPlanner.Surface = T9CandidateSourceControlPlanner.Surface.CHINESE,
        loadingState: ChineseT9CandidateLoadingState = ChineseT9CandidateLoadingState(),
        rawCandidatesEmpty: Boolean = false,
        pendingPunctuationActive: Boolean = false,
        compositionKeyCount: Int = 1,
        pendingPinyinSelection: Boolean = false,
        filterPrefixesEmpty: Boolean = true,
        chineseScheme: ChineseT9Scheme = ChineseT9Scheme.PINYIN,
        invalidReading: Boolean = false
    ): T9CandidateSourceControlPlanner.Input =
        T9CandidateSourceControlPlanner.Input(
            surface = surface,
            loadingState = loadingState,
            rawCandidatesEmpty = rawCandidatesEmpty,
            pendingPunctuationActive = pendingPunctuationActive,
            compositionKeyCount = compositionKeyCount,
            pendingPinyinSelection = pendingPinyinSelection,
            filterPrefixesEmpty = filterPrefixesEmpty,
            chineseScheme = chineseScheme,
            invalidReading = invalidReading
        )
}

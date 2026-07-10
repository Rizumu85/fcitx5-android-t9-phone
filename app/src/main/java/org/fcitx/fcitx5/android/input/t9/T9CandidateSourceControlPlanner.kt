/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9CandidateSourceControlPlanner {
    enum class Surface {
        CHINESE,
        SMART_ENGLISH,
        OTHER
    }

    enum class BulkAction {
        RESET,
        REQUEST
    }

    enum class FilterAction {
        EMPTY,
        CHINESE_PREFIX_FILTER,
        PASSTHROUGH
    }

    data class Input(
        val surface: Surface,
        val loadingState: ChineseT9CandidateLoadingState,
        val rawCandidatesEmpty: Boolean,
        val pendingPunctuationActive: Boolean,
        val compositionKeyCount: Int,
        val pendingPinyinSelection: Boolean,
        val filterPrefixesEmpty: Boolean,
        val usesPinyinCandidatePipeline: Boolean
    )

    data class Plan(
        val surface: Surface,
        val waitForChineseCandidates: Boolean,
        val deferRender: Boolean,
        val suppressEmptyCandidates: Boolean,
        val bulkAction: BulkAction,
        val filterAction: FilterAction,
        val pendingPinyinSelection: Boolean,
        private val pendingPunctuationActive: Boolean,
        private val filterPrefixesEmpty: Boolean
    ) {
        fun shouldBuildLocalBudget(hasBulkFilteredPage: Boolean, bulkFilterPending: Boolean): Boolean =
            surface == Surface.CHINESE &&
                !suppressEmptyCandidates &&
                !pendingPinyinSelection &&
                !pendingPunctuationActive &&
                filterPrefixesEmpty &&
                !hasBulkFilteredPage &&
                !bulkFilterPending &&
                !waitForChineseCandidates
    }

    fun surface(
        t9InputModeEnabled: Boolean,
        chineseActive: Boolean,
        smartEnglishActive: Boolean
    ): Surface =
        when {
            t9InputModeEnabled && chineseActive -> Surface.CHINESE
            t9InputModeEnabled && !chineseActive && smartEnglishActive -> Surface.SMART_ENGLISH
            else -> Surface.OTHER
        }

    fun plan(input: Input): Plan {
        val chineseActive = input.surface == Surface.CHINESE
        val waitForChineseCandidates = input.loadingState.shouldWaitForCandidates(
            chineseT9Active = chineseActive,
            compositionKeyCount = input.compositionKeyCount,
            hasPendingPunctuation = input.pendingPunctuationActive,
            pendingPinyinSelection = input.pendingPinyinSelection,
            rawCandidatesEmpty = input.rawCandidatesEmpty
        )
        val suppressEmptyCandidates = chineseActive &&
            !input.pendingPunctuationActive &&
            input.compositionKeyCount <= 0
        val deferRender = chineseActive &&
            waitForChineseCandidates &&
            !input.pendingPunctuationActive
        // The bulk pass exists to atomically filter Pinyin readings across pages. Stroke and
        // Zhuyin already receive a complete native page, so routing them through that pass only
        // delays their first visible frame and risks applying Pinyin-only freshness rules.
        val bulkAction = if (
            !chineseActive ||
            suppressEmptyCandidates ||
            input.pendingPinyinSelection ||
            waitForChineseCandidates ||
            input.pendingPunctuationActive ||
            !input.usesPinyinCandidatePipeline
        ) {
            BulkAction.RESET
        } else {
            BulkAction.REQUEST
        }
        val filterAction = when {
            suppressEmptyCandidates || input.pendingPinyinSelection || waitForChineseCandidates ->
                FilterAction.EMPTY
            chineseActive && input.usesPinyinCandidatePipeline ->
                FilterAction.CHINESE_PREFIX_FILTER
            else -> FilterAction.PASSTHROUGH
        }
        return Plan(
            surface = input.surface,
            waitForChineseCandidates = waitForChineseCandidates,
            deferRender = deferRender,
            suppressEmptyCandidates = suppressEmptyCandidates,
            bulkAction = bulkAction,
            filterAction = filterAction,
            pendingPinyinSelection = input.pendingPinyinSelection,
            pendingPunctuationActive = input.pendingPunctuationActive,
            filterPrefixesEmpty = input.filterPrefixesEmpty
        )
    }
}

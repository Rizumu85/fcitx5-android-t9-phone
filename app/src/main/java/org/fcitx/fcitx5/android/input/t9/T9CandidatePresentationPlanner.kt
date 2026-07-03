/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

object T9CandidatePresentationPlanner {

    enum class OriginalIndexSource {
        PENDING_PUNCTUATION,
        SMART_ENGLISH,
        BULK_FILTERED,
        PENDING_BULK_DISPLAY,
        LOCAL_BUDGET,
        PAGED
    }

    data class Input(
        val rawPaged: FcitxEvent.PagedCandidateEvent.Data,
        val filteredPaged: FcitxEvent.PagedCandidateEvent.Data,
        val filteredMatchedPrefix: String?,
        val smartEnglishPaged: FcitxEvent.PagedCandidateEvent.Data?,
        val pendingPunctuationPaged: FcitxEvent.PagedCandidateEvent.Data?,
        val localBudgetedPaged: FcitxEvent.PagedCandidateEvent.Data?,
        val bulkFilteredPaged: FcitxEvent.PagedCandidateEvent.Data?,
        val bulkFilteredMatchedPrefix: String?,
        val bulkFilterPending: Boolean,
        val chineseT9Active: Boolean,
        val suppressEmptyCandidates: Boolean,
        val pendingPinyinSelection: Boolean,
        val waitForChineseCandidates: Boolean
    )

    data class Plan(
        val candidateSource: FcitxEvent.PagedCandidateEvent.Data,
        val cursorSource: FcitxEvent.PagedCandidateEvent.Data,
        val applyChineseCursor: Boolean,
        val usesSmartEnglish: Boolean,
        val usesPendingPunctuation: Boolean,
        val usesBulkSelection: Boolean,
        val usesLocalBudget: Boolean,
        val matchedPrefix: String?,
        val originalIndexSource: OriginalIndexSource
    )

    fun plan(input: Input): Plan {
        val useBulkFiltered = input.chineseT9Active &&
            input.bulkFilteredPaged != null &&
            !input.bulkFilterPending
        val usePendingBulkDisplay = input.chineseT9Active &&
            input.bulkFilteredPaged != null &&
            input.bulkFilterPending
        val useLocalBudget = !useBulkFiltered &&
            !usePendingBulkDisplay &&
            input.localBudgetedPaged != null

        val candidateSource = when {
            input.pendingPunctuationPaged != null -> input.pendingPunctuationPaged
            input.smartEnglishPaged != null -> input.smartEnglishPaged
            useBulkFiltered || usePendingBulkDisplay -> requireNotNull(input.bulkFilteredPaged)
            input.localBudgetedPaged != null -> input.localBudgetedPaged
            else -> input.filteredPaged
        }

        val cursorSource = when {
            input.pendingPunctuationPaged != null -> input.pendingPunctuationPaged
            input.smartEnglishPaged != null -> input.smartEnglishPaged
            input.suppressEmptyCandidates || input.pendingPinyinSelection || input.waitForChineseCandidates ->
                FcitxEvent.PagedCandidateEvent.Data.Empty
            input.chineseT9Active -> candidateSource
            else -> input.rawPaged
        }

        val originalIndexSource = when {
            input.pendingPunctuationPaged != null -> OriginalIndexSource.PENDING_PUNCTUATION
            input.smartEnglishPaged != null -> OriginalIndexSource.SMART_ENGLISH
            useBulkFiltered -> OriginalIndexSource.BULK_FILTERED
            usePendingBulkDisplay -> OriginalIndexSource.PENDING_BULK_DISPLAY
            input.localBudgetedPaged != null -> OriginalIndexSource.LOCAL_BUDGET
            else -> OriginalIndexSource.PAGED
        }

        return Plan(
            candidateSource = candidateSource,
            cursorSource = cursorSource,
            applyChineseCursor = input.pendingPunctuationPaged == null &&
                input.smartEnglishPaged == null &&
                !input.suppressEmptyCandidates &&
                !input.pendingPinyinSelection &&
                !input.waitForChineseCandidates &&
                input.chineseT9Active,
            usesSmartEnglish = input.smartEnglishPaged != null && input.pendingPunctuationPaged == null,
            usesPendingPunctuation = input.pendingPunctuationPaged != null,
            usesBulkSelection = input.pendingPunctuationPaged == null && useBulkFiltered,
            usesLocalBudget = input.pendingPunctuationPaged == null && useLocalBudget,
            matchedPrefix = when {
                input.pendingPunctuationPaged != null -> null
                useBulkFiltered -> input.bulkFilteredMatchedPrefix
                else -> input.filteredMatchedPrefix
            },
            originalIndexSource = originalIndexSource
        )
    }
}

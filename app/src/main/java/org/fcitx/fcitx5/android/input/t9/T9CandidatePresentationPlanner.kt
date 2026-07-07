/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

object T9CandidatePresentationPlanner {
    data class Input(
        val rawPaged: FcitxEvent.PagedCandidateEvent.Data,
        val filteredPaged: T9PagedCandidates,
        val filteredMatchedPrefix: String?,
        val smartEnglishPaged: T9PagedCandidates?,
        val pendingPunctuationPaged: T9PagedCandidates?,
        val localBudgetedPaged: T9PagedCandidates?,
        val bulkFilteredPaged: T9PagedCandidates?,
        val bulkFilteredMatchedPrefix: String?,
        val bulkFilterPending: Boolean,
        val chineseT9Active: Boolean,
        val suppressEmptyCandidates: Boolean,
        val pendingPinyinSelection: Boolean,
        val waitForChineseCandidates: Boolean
    )

    data class Plan(
        val candidateSource: T9PagedCandidates,
        val cursorSource: T9PagedCandidates,
        val applyChineseCursor: Boolean,
        val usesSmartEnglish: Boolean,
        val usesPendingPunctuation: Boolean,
        val usesBulkSelection: Boolean,
        val usesLocalBudget: Boolean,
        val matchedPrefix: String?
    )

    fun plan(input: Input): Plan {
        val rawPaged = T9PagedCandidates.passthrough(input.rawPaged)
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
        }.let { source ->
            if (usePendingBulkDisplay) {
                source.withOriginalIndices(IntArray(source.data.candidates.size) { -1 })
            } else {
                source
            }
        }

        val cursorSource = when {
            input.pendingPunctuationPaged != null -> input.pendingPunctuationPaged
            input.smartEnglishPaged != null -> input.smartEnglishPaged
            input.suppressEmptyCandidates || input.pendingPinyinSelection || input.waitForChineseCandidates ->
                T9PagedCandidates.Empty
            input.chineseT9Active -> candidateSource
            else -> rawPaged
        }

        return Plan(
            candidateSource = candidateSource,
            cursorSource = cursorSource,
            applyChineseCursor = input.pendingPunctuationPaged == null &&
                input.smartEnglishPaged == null &&
                !useBulkFiltered &&
                !usePendingBulkDisplay &&
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
            }
        )
    }
}

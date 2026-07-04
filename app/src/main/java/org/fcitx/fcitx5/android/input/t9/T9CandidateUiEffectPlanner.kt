/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9CandidateUiEffectPlanner {
    enum class BulkEffect {
        NONE,
        RESET,
        REQUEST
    }

    data class LocalBudgetDecision(
        val buildLocalBudgetPage: Boolean,
        val resetLocalBudget: Boolean
    )

    fun bulkEffect(
        isChineseSurface: Boolean,
        suppressEmptyCandidates: Boolean,
        pendingPinyinSelection: Boolean,
        waitForChineseCandidates: Boolean,
        hasPendingPunctuation: Boolean
    ): BulkEffect =
        when {
            !isChineseSurface ||
                suppressEmptyCandidates ||
                pendingPinyinSelection ||
                waitForChineseCandidates ||
                hasPendingPunctuation -> BulkEffect.RESET
            else -> BulkEffect.REQUEST
        }

    fun localBudgetDecision(
        suppressEmptyCandidates: Boolean,
        pendingPinyinSelection: Boolean,
        hasPendingPunctuation: Boolean,
        chineseT9Active: Boolean,
        hasFilterPrefixes: Boolean,
        hasBulkFilteredPage: Boolean,
        waitForChineseCandidates: Boolean
    ): LocalBudgetDecision {
        val build = !suppressEmptyCandidates &&
            !pendingPinyinSelection &&
            !hasPendingPunctuation &&
            chineseT9Active &&
            !hasFilterPrefixes &&
            !hasBulkFilteredPage &&
            !waitForChineseCandidates
        return LocalBudgetDecision(
            buildLocalBudgetPage = build,
            resetLocalBudget = !build
        )
    }
}

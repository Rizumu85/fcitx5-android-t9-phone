/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9CandidateSurfaceLayout {
    data class Input(
        val candidateMeasuredWidthPx: Int?,
        val pinyinCount: Int,
        val pinyinFullContentWidthPx: Int,
        val pinyinFoldedContentWidthPx: Int,
        val maxRowWidthPx: Int,
        val minVisiblePinyinChips: Int,
        val pinyinRowFocused: Boolean
    )

    data class Plan(
        val contentReady: Boolean,
        val rowWidthPx: Int?,
        val pinyin: T9PinyinOverflowPolicy.Plan?
    )

    fun plan(input: Input): Plan {
        val candidateWidth = input.candidateMeasuredWidthPx
            ?.takeIf { it > 0 }
            ?: return Plan(contentReady = false, rowWidthPx = null, pinyin = null)
        val maxWidth = input.maxRowWidthPx.coerceAtLeast(1)
        val safeCandidateWidth = candidateWidth.coerceAtMost(maxWidth).coerceAtLeast(1)
        if (input.pinyinCount <= 0) {
            return Plan(
                contentReady = true,
                rowWidthPx = safeCandidateWidth,
                pinyin = null
            )
        }
        val pinyinPlan = T9PinyinOverflowPolicy.plan(
            pinyinCount = input.pinyinCount,
            fullContentWidthPx = input.pinyinFullContentWidthPx,
            candidateRowWidthPx = safeCandidateWidth,
            maxRowWidthPx = maxWidth,
            minVisibleChips = input.minVisiblePinyinChips,
            pinyinRowFocused = input.pinyinRowFocused
        )
        // Product decision: Chinese T9 bubble width follows the real Hanzi row once it is
        // measured, while the pinyin filter may still widen short Hanzi pages by its folded row.
        // Estimation stays in paging; visual proportions come from measured UI.
        val pinyinWidth = if (pinyinPlan.folded) {
            input.pinyinFoldedContentWidthPx
        } else {
            input.pinyinFullContentWidthPx
        }
        return Plan(
            contentReady = true,
            rowWidthPx = maxOf(safeCandidateWidth, pinyinWidth.coerceAtMost(maxWidth))
                .coerceAtLeast(1),
            pinyin = pinyinPlan
        )
    }
}

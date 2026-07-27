/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9CandidateWidthBudget(
    val maxWidthPx: Int,
    val candidateSpacingPx: Int,
    private val candidateHorizontalPaddingPx: Int,
    private val minimumCandidateWidthPx: Int,
    val rowHorizontalPaddingPx: Int,
    private val measureTextWidthPx: (String) -> Int
) {
    val signature: String =
        "${maxWidthPx.coerceAtLeast(1)}|" +
            "${candidateSpacingPx.coerceAtLeast(0)}|" +
            "${candidateHorizontalPaddingPx.coerceAtLeast(0)}|" +
            "${minimumCandidateWidthPx.coerceAtLeast(1)}|" +
            rowHorizontalPaddingPx.coerceAtLeast(0)

    val maxCandidateWidthPx: Int
        get() = (maxWidthPx - rowHorizontalPaddingPx.coerceAtLeast(0) * 2)
            .coerceAtLeast(minimumCandidateWidthPx)

    fun naturalCandidateWidthPx(candidate: FcitxEvent.Candidate): Int =
        (measureTextWidthPx(candidate.text) + candidateHorizontalPaddingPx * 2)
            .coerceAtLeast(minimumCandidateWidthPx)
            .coerceAtMost(maxCandidateWidthPx)

    fun rowWidthPx(
        candidates: List<FcitxEvent.Candidate>,
        hasTrailingItem: Boolean = false,
        trailingItemWidthPx: Int = 0
    ): Int {
        if (candidates.isEmpty()) return 0
        val candidateWidths = candidates.map(::naturalCandidateWidthPx)
        val spacingCount = candidates.lastIndex + if (hasTrailingItem) 1 else 0
        // Product decision: candidate focus is expressed by color and background, not transforms.
        // Natural widths therefore remain the single paging and layout geometry.
        return candidateWidths.sum() +
            candidateSpacingPx.coerceAtLeast(0) * spacingCount +
            trailingItemWidthPx.coerceAtLeast(0) +
            rowHorizontalPaddingPx.coerceAtLeast(0) * 2
    }
}

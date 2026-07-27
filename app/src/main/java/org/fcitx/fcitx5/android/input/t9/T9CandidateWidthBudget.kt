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
    val activeScalePercent: Int = T9CandidateFocusEnvelope.DEFAULT_SCALE_PERCENT,
    private val measureTextWidthPx: (String) -> Int
) {
    val signature: String =
        "${maxWidthPx.coerceAtLeast(1)}|" +
            "${candidateSpacingPx.coerceAtLeast(0)}|" +
            "${candidateHorizontalPaddingPx.coerceAtLeast(0)}|" +
            "${minimumCandidateWidthPx.coerceAtLeast(1)}|" +
            "${rowHorizontalPaddingPx.coerceAtLeast(0)}|" +
            activeScalePercent

    val maxCandidateWidthPx: Int
        get() = (maxWidthPx - candidateSpacingPx).coerceAtLeast(minimumCandidateWidthPx)

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
        val focusMargins = T9CandidateFocusEnvelope.candidateEndMarginsPx(
            candidateWidthsPx = candidateWidths,
            itemSpacingPx = candidateSpacingPx,
            hasTrailingItem = hasTrailingItem,
            scalePercent = activeScalePercent
        )
        // The pager and Android row must reserve the same potential focus growth. Counting every
        // chip as focused at once under-fills pages and can manufacture a one-candidate tail.
        return candidateWidths.sum() +
            focusMargins.sum() +
            trailingItemWidthPx.coerceAtLeast(0) +
            rowHorizontalPaddingPx.coerceAtLeast(0) * 2
    }
}

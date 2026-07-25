/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlin.math.ceil
import org.fcitx.fcitx5.android.core.FcitxEvent

class T9CandidateWidthBudget(
    val maxWidthPx: Int,
    val candidateSpacingPx: Int,
    private val candidateHorizontalPaddingPx: Int,
    private val minimumCandidateWidthPx: Int,
    val activeScalePercent: Int = T9CandidateFocusEnvelope.DEFAULT_SCALE_PERCENT,
    private val measureTextWidthPx: (String) -> Int
) {
    val signature: String =
        "${maxWidthPx.coerceAtLeast(1)}|" +
            "${candidateSpacingPx.coerceAtLeast(0)}|" +
            "${candidateHorizontalPaddingPx.coerceAtLeast(0)}|" +
            "${minimumCandidateWidthPx.coerceAtLeast(1)}|" +
            activeScalePercent

    val maxCandidateWidthPx: Int
        get() = (maxWidthPx - candidateSpacingPx).coerceAtLeast(minimumCandidateWidthPx)

    fun naturalCandidateWidthPx(candidate: FcitxEvent.Candidate): Int =
        (measureTextWidthPx(candidate.text) + candidateHorizontalPaddingPx * 2)
            .coerceAtLeast(minimumCandidateWidthPx)
            .coerceAtMost(maxCandidateWidthPx)

    fun candidateWidthPx(candidate: FcitxEvent.Candidate): Int =
        candidateWidthPx(candidate, active = true)

    fun candidateWidthPx(candidate: FcitxEvent.Candidate, active: Boolean): Int {
        val naturalWidth = naturalCandidateWidthPx(candidate)
        val scaledWidth = if (active) {
            ceil(naturalWidth * activeScalePercent / 100f).toInt()
        } else {
            naturalWidth
        }
        return scaledWidth + candidateSpacingPx
    }
}

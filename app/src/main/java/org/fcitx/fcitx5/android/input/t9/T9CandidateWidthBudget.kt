/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlin.math.ceil
import org.fcitx.fcitx5.android.core.FcitxEvent

class T9CandidateWidthBudget private constructor(
    val maxWidthPx: Int,
    val candidateSpacingPx: Int,
    private val minimumCandidateWidthPx: Int,
    private val activeScalePercent: Int,
    private val measurementSignature: String,
    private val measureCandidateNaturalWidthPx: (FcitxEvent.Candidate, maxCandidateWidthPx: Int) -> Int
) {
    constructor(
        maxWidthPx: Int,
        candidateSpacingPx: Int,
        candidateHorizontalPaddingPx: Int,
        minimumCandidateWidthPx: Int,
        activeScalePercent: Int = ACTIVE_SCALE_PERCENT,
        measureTextWidthPx: (String) -> Int
    ) : this(
        maxWidthPx = maxWidthPx,
        candidateSpacingPx = candidateSpacingPx,
        minimumCandidateWidthPx = minimumCandidateWidthPx,
        activeScalePercent = activeScalePercent,
        measurementSignature = "text|${candidateHorizontalPaddingPx.coerceAtLeast(0)}",
        measureCandidateNaturalWidthPx = { candidate, maxCandidateWidthPx ->
            (measureTextWidthPx(candidate.text) + candidateHorizontalPaddingPx * 2)
                .coerceAtLeast(minimumCandidateWidthPx)
                .coerceAtMost(maxCandidateWidthPx)
        }
    )

    val signature: String =
        "${maxWidthPx.coerceAtLeast(1)}|" +
            "${candidateSpacingPx.coerceAtLeast(0)}|" +
            "${minimumCandidateWidthPx.coerceAtLeast(1)}|" +
            "$activeScalePercent|" +
            measurementSignature

    val maxCandidateWidthPx: Int
        get() = (maxWidthPx - candidateSpacingPx).coerceAtLeast(minimumCandidateWidthPx)

    fun candidateWidthPx(candidate: FcitxEvent.Candidate): Int =
        candidateWidthPx(candidate, active = true)

    fun candidateWidthPx(candidate: FcitxEvent.Candidate, active: Boolean): Int {
        val naturalWidth = measureCandidateNaturalWidthPx(candidate, maxCandidateWidthPx)
            .coerceAtLeast(minimumCandidateWidthPx)
            .coerceAtMost(maxCandidateWidthPx)
        val scaledWidth = if (active) {
            ceil(naturalWidth * activeScalePercent / 100f).toInt()
        } else {
            naturalWidth
        }
        return scaledWidth + candidateSpacingPx
    }

    companion object {
        private const val ACTIVE_SCALE_PERCENT = 107

        fun measuredCandidates(
            maxWidthPx: Int,
            candidateSpacingPx: Int,
            minimumCandidateWidthPx: Int,
            activeScalePercent: Int = ACTIVE_SCALE_PERCENT,
            measurementSignature: String,
            measureCandidateWidthPx: (FcitxEvent.Candidate, maxCandidateWidthPx: Int) -> Int
        ): T9CandidateWidthBudget =
            T9CandidateWidthBudget(
                maxWidthPx = maxWidthPx,
                candidateSpacingPx = candidateSpacingPx,
                minimumCandidateWidthPx = minimumCandidateWidthPx,
                activeScalePercent = activeScalePercent,
                measurementSignature = "measured|$measurementSignature",
                measureCandidateNaturalWidthPx = measureCandidateWidthPx
            )
    }
}

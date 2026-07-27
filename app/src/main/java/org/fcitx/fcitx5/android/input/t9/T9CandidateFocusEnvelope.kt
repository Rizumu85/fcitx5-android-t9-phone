/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlin.math.ceil
import kotlin.math.max

/**
 * Reserves a stable layout envelope for shortcut candidates before any one of them is focused.
 *
 * Focus is a drawing transform and therefore does not participate in Android measurement. Keeping
 * the reserve independent of the current cursor prevents long candidates from overlapping their
 * neighbour without making the bubble resize as focus moves.
 */
object T9CandidateFocusEnvelope {
    const val DEFAULT_SCALE_PERCENT = 107

    data class Overflow(
        val startPx: Int,
        val endPx: Int
    )

    fun scaleFactor(scalePercent: Int = DEFAULT_SCALE_PERCENT): Float =
        scalePercent.coerceAtLeast(100) / 100f

    fun maxUnfocusedCandidateWidthPx(
        maxRowWidthPx: Int,
        edgePaddingPx: Int,
        scalePercent: Int = DEFAULT_SCALE_PERCENT
    ): Int {
        val availableWidth = (maxRowWidthPx - edgePaddingPx.coerceAtLeast(0) * 2)
            .coerceAtLeast(1)
        return (
            availableWidth.toLong() * 100L /
                scalePercent.coerceAtLeast(100)
            )
            .coerceAtLeast(1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun overflows(
        candidateWidthsPx: List<Int>,
        edgeAlignLastCandidate: Boolean,
        scalePercent: Int = DEFAULT_SCALE_PERCENT
    ): List<Overflow> =
        candidateWidthsPx.mapIndexed { index, widthPx ->
            val growthPx = ceil(
                widthPx.coerceAtLeast(0) *
                    (scalePercent.coerceAtLeast(100) - 100) /
                    100f
            ).toInt()
            when {
                index == 0 -> {
                    // The leading candidate grows only toward the row so its accepted left inset
                    // stays fixed. Its entire focus growth belongs to the following boundary.
                    Overflow(startPx = 0, endPx = growthPx)
                }
                edgeAlignLastCandidate && index == candidateWidthsPx.lastIndex -> {
                    // The final candidate mirrors the first one: focus grows into the row instead
                    // of requiring a content-dependent reserve after the last visible word.
                    Overflow(startPx = growthPx, endPx = 0)
                }
                else -> {
                    val startPx = growthPx / 2
                    Overflow(startPx = startPx, endPx = growthPx - startPx)
                }
            }
        }

    fun candidateEndMarginsPx(
        candidateWidthsPx: List<Int>,
        itemSpacingPx: Int,
        hasTrailingItem: Boolean,
        scalePercent: Int = DEFAULT_SCALE_PERCENT
    ): List<Int> {
        if (candidateWidthsPx.isEmpty()) return emptyList()
        val spacing = itemSpacingPx.coerceAtLeast(0)
        val edgeAlignLastCandidate = !hasTrailingItem
        val overflow = overflows(candidateWidthsPx, edgeAlignLastCandidate, scalePercent)
        return overflow.indices.map { index ->
            if (index < overflow.lastIndex) {
                spacing + max(overflow[index].endPx, overflow[index + 1].startPx)
            } else if (edgeAlignLastCandidate) {
                // A single item owns both logical edges, but one transform cannot anchor to both.
                // Anchor it to the accepted leading inset and reserve its growth before the tail.
                overflow[index].endPx.takeIf { index == 0 } ?: 0
            } else {
                spacing + overflow[index].endPx
            }
        }
    }
}

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

    fun overflows(
        candidateWidthsPx: List<Int>,
        scalePercent: Int = DEFAULT_SCALE_PERCENT
    ): List<Overflow> =
        candidateWidthsPx.mapIndexed { index, widthPx ->
            val growthPx = ceil(
                widthPx.coerceAtLeast(0) *
                    (scalePercent.coerceAtLeast(100) - 100) /
                    100f
            ).toInt()
            if (index == 0) {
                // The leading candidate grows only toward the row so its accepted left inset stays
                // fixed. Its entire focus growth therefore belongs to the following boundary.
                Overflow(startPx = 0, endPx = growthPx)
            } else {
                val startPx = growthPx / 2
                Overflow(startPx = startPx, endPx = growthPx - startPx)
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
        val overflow = overflows(candidateWidthsPx, scalePercent)
        return overflow.indices.map { index ->
            if (index < overflow.lastIndex) {
                spacing + max(overflow[index].endPx, overflow[index + 1].startPx)
            } else {
                (if (hasTrailingItem) spacing else 0) + overflow[index].endPx
            }
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

internal object T9SemanticSymbolStyle {
    const val NormalFontWeight = 400
    const val BoldFontWeight = 700

    fun strokeEmForWeight(fontWeight: Int): Float {
        val weight = fontWeight.coerceIn(MIN_FONT_WEIGHT, MAX_FONT_WEIGHT)
        return if (weight <= NormalFontWeight) {
            lerp(
                start = THIN_STROKE_EM,
                end = NORMAL_STROKE_EM,
                fraction = (weight - MIN_FONT_WEIGHT).toFloat() /
                    (NormalFontWeight - MIN_FONT_WEIGHT)
            )
        } else {
            lerp(
                start = NORMAL_STROKE_EM,
                end = HEAVY_STROKE_EM,
                fraction = (weight - NormalFontWeight).toFloat() /
                    (MAX_FONT_WEIGHT - NormalFontWeight)
            )
        }
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction

    private const val MIN_FONT_WEIGHT = 100
    private const val MAX_FONT_WEIGHT = 900
    private const val THIN_STROKE_EM = 0.070f
    private const val NORMAL_STROKE_EM = 0.095f
    private const val HEAVY_STROKE_EM = 0.140f
}

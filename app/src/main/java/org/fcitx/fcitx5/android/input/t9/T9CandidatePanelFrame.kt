/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlin.math.roundToInt

object T9CandidatePanelFrame {
    fun oneLineRowHeight(
        fontSizeSp: Int,
        scaledDensity: Float,
        verticalPaddingPx: Int
    ): Int {
        val linePx = fontSizeSp * scaledDensity * OneLineMultiplier
        return (linePx + 2 * verticalPaddingPx).roundToInt()
    }

    fun shortcutRowHeight(
        fontSizeSp: Int,
        scaledDensity: Float,
        verticalPaddingPx: Int
    ): Int {
        val twoLinePx = fontSizeSp * scaledDensity * ShortcutLineMultiplier
        return (twoLinePx + 2 * verticalPaddingPx).roundToInt()
            .coerceAtLeast(oneLineRowHeight(fontSizeSp, scaledDensity, verticalPaddingPx))
    }

    private const val OneLineMultiplier = 1.2f
    private const val ShortcutLineMultiplier = 2.06f
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlin.math.max

class T9CandidatePanelWidthState {
    private var rememberedWidthPx = 0

    val rememberedWidth: Int
        get() = rememberedWidthPx

    fun reset() {
        rememberedWidthPx = 0
    }

    fun remember(widthPx: Int): Int {
        if (widthPx <= 0) return rememberedWidthPx
        rememberedWidthPx = max(rememberedWidthPx, widthPx)
        return rememberedWidthPx
    }

    fun currentOrNull(): Int? = rememberedWidthPx.takeIf { it > 0 }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class T9CandidatePanelWidthState {
    private var currentWidthPx = 0

    val currentWidth: Int
        get() = currentWidthPx

    fun reset() {
        currentWidthPx = 0
    }

    fun update(widthPx: Int): Int {
        if (widthPx <= 0) return currentWidthPx
        currentWidthPx = widthPx
        return currentWidthPx
    }

    fun currentOrNull(): Int? = currentWidthPx.takeIf { it > 0 }
}

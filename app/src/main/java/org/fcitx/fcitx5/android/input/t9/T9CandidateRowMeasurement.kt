/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class T9CandidateRowMeasurement {
    var currentSignature: String = ""
        private set
    private var measuredSignature: String = ""
    private var measuredWidthPx: Int = 0

    fun markContent(signature: String): Boolean {
        if (currentSignature == signature) return false
        currentSignature = signature
        measuredSignature = ""
        measuredWidthPx = 0
        return true
    }

    fun remember(widthPx: Int, signature: String = currentSignature): Int {
        if (widthPx > 0 && signature == currentSignature) {
            measuredSignature = signature
            measuredWidthPx = widthPx
        }
        return widthPx
    }

    fun currentWidthPx(): Int? =
        measuredWidthPx.takeIf {
            it > 0 && measuredSignature == currentSignature
        }

    fun clear() {
        currentSignature = ""
        measuredSignature = ""
        measuredWidthPx = 0
    }
}

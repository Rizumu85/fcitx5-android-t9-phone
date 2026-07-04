/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

enum class T9CandidateFocus {
    TOP,
    BOTTOM
}

class T9CandidateFocusController(
    private val onFocusChanged: () -> Unit = {}
) {
    var current: T9CandidateFocus = T9CandidateFocus.BOTTOM
        private set

    fun reset() {
        current = T9CandidateFocus.BOTTOM
    }

    fun moveTo(focus: T9CandidateFocus) {
        current = focus
        onFocusChanged()
    }
}

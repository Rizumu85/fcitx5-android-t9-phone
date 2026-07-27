/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class ChinesePredictionModeController(
    initialEnabled: Boolean,
    private val setPreference: (Boolean) -> Unit,
    private val onEnabledChanged: (Boolean) -> Unit,
    private val showModeIndicator: (Boolean) -> Unit
) {
    var enabled: Boolean = initialEnabled
        private set

    fun onPreferenceChanged(value: Boolean) {
        if (enabled == value) return
        enabled = value
        onEnabledChanged(value)
    }

    fun toggle() {
        val next = !enabled
        enabled = next
        setPreference(next)
        onEnabledChanged(next)
        showModeIndicator(next)
    }
}

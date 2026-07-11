/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

internal class PhysicalT9KeyFlowSession {
    // Long-press state intentionally lives outside mode Modules so a key-down in one mode and the
    // matching key-up after a mode switch still resolve through one physical-key timeline.
    private val keyLongPressFlags = BooleanArray(KeyEvent.KEYCODE_STAR + 1)

    var poundLongPressTriggered: Boolean = false
    var pendingSmartEnglishDigitKeyCode: Int? = null
    var pendingSmartEnglishDigit: Int = -1
    private var deferredVoiceInputKeyCode: Int? = null

    fun resetSmartEnglishPendingDigit() {
        pendingSmartEnglishDigitKeyCode = null
        pendingSmartEnglishDigit = -1
    }

    fun isDigitLongPressFlagSet(keyCode: Int): Boolean =
        keyLongPressFlags.getOrNull(keyCode) == true

    fun setDigitLongPressFlag(keyCode: Int, value: Boolean) {
        if (keyCode in keyLongPressFlags.indices) {
            keyLongPressFlags[keyCode] = value
        }
    }

    fun deferVoiceInputUntilKeyUp(keyCode: Int) {
        deferredVoiceInputKeyCode = keyCode
    }

    fun consumeDeferredVoiceInput(keyCode: Int): Boolean {
        if (deferredVoiceInputKeyCode != keyCode) return false
        deferredVoiceInputKeyCode = null
        return true
    }

    fun clearDeferredVoiceInput(keyCode: Int) {
        if (deferredVoiceInputKeyCode == keyCode) deferredVoiceInputKeyCode = null
    }
}

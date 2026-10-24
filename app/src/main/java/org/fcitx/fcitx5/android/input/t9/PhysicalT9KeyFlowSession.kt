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
    private val deferredIdleDigitKeys = BooleanArray(KeyEvent.KEYCODE_STAR + 1)

    var poundLongPressTriggered: Boolean = false
    // Physical T9 users roll presses: the next digit can go down before the previous one is
    // released, so every held digit keeps its own pending press until its own release.
    private val pendingSmartEnglishDigitKeys = BooleanArray(KeyEvent.KEYCODE_STAR + 1)
    private var deferredVoiceInputKeyCode: Int? = null

    fun resetSmartEnglishPendingDigit() {
        pendingSmartEnglishDigitKeys.fill(false)
    }

    fun setSmartEnglishDigitPending(keyCode: Int, value: Boolean) {
        if (keyCode in pendingSmartEnglishDigitKeys.indices) {
            pendingSmartEnglishDigitKeys[keyCode] = value
        }
    }

    fun consumeSmartEnglishDigitPending(keyCode: Int): Boolean {
        if (pendingSmartEnglishDigitKeys.getOrNull(keyCode) != true) return false
        pendingSmartEnglishDigitKeys[keyCode] = false
        return true
    }

    fun isDigitLongPressFlagSet(keyCode: Int): Boolean =
        keyLongPressFlags.getOrNull(keyCode) == true

    fun setDigitLongPressFlag(keyCode: Int, value: Boolean) {
        if (keyCode in keyLongPressFlags.indices) {
            keyLongPressFlags[keyCode] = value
        }
    }

    fun setIdleDigitDeferred(keyCode: Int, value: Boolean) {
        if (keyCode in deferredIdleDigitKeys.indices) {
            deferredIdleDigitKeys[keyCode] = value
        }
    }

    fun consumeDeferredIdleDigit(keyCode: Int): Boolean {
        if (deferredIdleDigitKeys.getOrNull(keyCode) != true) return false
        deferredIdleDigitKeys[keyCode] = false
        return true
    }

    /** Forgets every pending decision of a press whose release was cancelled by the system. */
    fun cancelPress(keyCode: Int) {
        setDigitLongPressFlag(keyCode, false)
        setIdleDigitDeferred(keyCode, false)
        setSmartEnglishDigitPending(keyCode, false)
        clearDeferredVoiceInput(keyCode)
        if (keyCode == KeyEvent.KEYCODE_POUND) poundLongPressTriggered = false
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

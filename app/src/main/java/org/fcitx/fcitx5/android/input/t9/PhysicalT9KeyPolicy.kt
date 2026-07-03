/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

object PhysicalT9KeyPolicy {

    enum class FocusKey {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        OK
    }

    fun t9Digit(keyCode: Int): Int? = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
        else -> null
    }

    fun isPredictiveDigitKey(keyCode: Int): Boolean =
        t9Digit(keyCode)?.let { it in 2..9 } == true

    fun decimalDigit(keyCode: Int): Int? = t9Digit(keyCode) ?: when (keyCode) {
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
            keyCode - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }

    fun candidateShortcutIndex(keyCode: Int): Int? = when (keyCode) {
        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_1
        KeyEvent.KEYCODE_0 -> 9
        else -> null
    }

    fun isDeleteKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DEL,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_FORWARD_DEL -> true
        else -> false
    }

    fun focusKey(keyCode: Int): FocusKey? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> FocusKey.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> FocusKey.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> FocusKey.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> FocusKey.RIGHT
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> FocusKey.OK
        else -> null
    }

    fun isHorizontalFocusKey(keyCode: Int): Boolean = when (focusKey(keyCode)) {
        FocusKey.LEFT, FocusKey.RIGHT -> true
        else -> false
    }

    fun isPendingPunctuationControlKey(keyCode: Int): Boolean =
        t9Digit(keyCode) != null ||
            keyCode == KeyEvent.KEYCODE_STAR ||
            keyCode == KeyEvent.KEYCODE_POUND ||
            isDeleteKey(keyCode) ||
            focusKey(keyCode) != null
}

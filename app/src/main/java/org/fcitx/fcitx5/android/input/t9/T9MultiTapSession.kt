/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

class T9MultiTapSession(
    private val timeoutMillis: Long = DefaultTimeoutMillis,
    private val keyMap: Map<Int, String> = DefaultKeyMap
) {
    data class KeyPressResult(
        val committedPrevious: Char?,
        val pendingChar: Char
    )

    private var lastKey = -1
    private var lastTime = 0L
    private var index = 0
    private var pendingChar: Char? = null

    val hasPendingChar: Boolean
        get() = pendingChar != null

    fun pendingChar(): Char? = pendingChar

    fun handleKey(keyCode: Int, eventTimeMillis: Long): KeyPressResult? {
        val letters = keyMap[keyCode] ?: return null
        val committedPrevious =
            if (keyCode == lastKey && eventTimeMillis - lastTime < timeoutMillis) {
                index = (index + 1) % letters.length
                null
            } else {
                val committed = pendingChar
                index = 0
                committed
            }

        lastKey = keyCode
        lastTime = eventTimeMillis
        pendingChar = letters[index]

        return KeyPressResult(
            committedPrevious = committedPrevious,
            pendingChar = pendingChar ?: letters.first()
        )
    }

    fun commitPending(): Char? {
        val committed = pendingChar
        clear()
        return committed
    }

    fun cancelPending(): Boolean {
        val hadPending = pendingChar != null
        clear()
        return hadPending
    }

    fun reset(): Boolean {
        val hadPending = pendingChar != null
        clear()
        return hadPending
    }

    private fun clear() {
        pendingChar = null
        lastKey = -1
        lastTime = 0L
        index = 0
    }

    companion object {
        const val DefaultTimeoutMillis = 1200L

        val DefaultKeyMap: Map<Int, String> = mapOf(
            KeyEvent.KEYCODE_1 to ",.?!'\"-@/:",
            KeyEvent.KEYCODE_2 to "abc",
            KeyEvent.KEYCODE_3 to "def",
            KeyEvent.KEYCODE_4 to "ghi",
            KeyEvent.KEYCODE_5 to "jkl",
            KeyEvent.KEYCODE_6 to "mno",
            KeyEvent.KEYCODE_7 to "pqrs",
            KeyEvent.KEYCODE_8 to "tuv",
            KeyEvent.KEYCODE_9 to "wxyz"
        )
    }
}

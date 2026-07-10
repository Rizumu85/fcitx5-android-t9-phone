/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class ChineseT9RawCodeSession {
    private val code = StringBuilder()
    private var digitCount = 0

    var revision: Long = 0
        private set

    val rawCode: String
        get() = code.toString()

    val keyCount: Int
        get() = digitCount

    val digitSequence: String
        get() = code.toString()

    val currentSegment: String
        get() = code.toString()

    fun append(digit: Int) {
        code.append(digit)
        digitCount += 1
        revision += 1
    }

    fun backspace() {
        if (code.isEmpty()) return
        digitCount -= 1
        code.deleteCharAt(code.lastIndex)
        revision += 1
    }

    fun clear() {
        if (code.isEmpty()) return
        code.clear()
        digitCount = 0
        revision += 1
    }

    fun isEmpty(): Boolean = code.isEmpty()
}

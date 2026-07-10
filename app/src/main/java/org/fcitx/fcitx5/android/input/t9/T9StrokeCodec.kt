/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9StrokeCodec {
    private val displayByDigit = mapOf(
        '1' to '一',
        '2' to '丨',
        '3' to '丿',
        '4' to '丶',
        '5' to '乛',
        '6' to '？'
    )

    fun display(rawCode: String): String? {
        if (rawCode.any { it !in displayByDigit }) return null
        return buildString(rawCode.length) {
            rawCode.forEach { append(displayByDigit.getValue(it)) }
        }
    }

    fun literalCommitText(rawCode: String, preview: String): String? =
        display(rawCode)?.takeIf { it == preview }

    fun digitsFromEnginePreedit(preedit: String): String = buildString {
        preedit.forEach { char ->
            when (char) {
                '一', '⼀', '㇐' -> append('1')
                '丨', '⼁', '㇑' -> append('2')
                '丿', '⼃', '㇒' -> append('3')
                '丶', '⼂', '㇏' -> append('4')
                '乛', '⼄', '㇠' -> append('5')
                '？' -> append('6')
            }
        }
    }
}

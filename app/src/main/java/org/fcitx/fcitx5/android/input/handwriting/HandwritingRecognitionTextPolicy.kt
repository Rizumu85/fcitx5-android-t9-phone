/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

internal object HandwritingRecognitionTextPolicy {
    private val commonSymbols = setOf(
        "，", "。", "、", "！", "？", "；", "：", "…", "—", "·", "～",
        "“", "”", "‘", "’", "（", "）", "《", "》",
        ",", ".", "!", "?", ";", ":", "-", "_", "/", "\\",
        "(", ")", "[", "]", "{", "}", "+", "=", "@", "#", "%", "&", "*"
    )

    fun accepts(text: String): Boolean = text.isSingleHanCharacter() || text in commonSymbols

    private fun String.isSingleHanCharacter(): Boolean {
        if (codePointCount(0, length) != 1) return false
        return Character.UnicodeScript.of(codePointAt(0)) == Character.UnicodeScript.HAN
    }
}

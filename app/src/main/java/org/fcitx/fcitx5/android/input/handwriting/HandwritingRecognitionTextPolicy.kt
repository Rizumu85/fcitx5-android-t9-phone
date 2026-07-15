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

    private val englishWord = Regex("[A-Za-z]+(?:['’\\-][A-Za-z]+)*")

    fun normalize(language: HandwritingLanguage, rawText: String): String? {
        val text = rawText.trim()
        if (text.isEmpty() || text.length > MaximumTextLength) return null
        return text.takeIf {
            when (language) {
                HandwritingLanguage.CHINESE -> text.isSingleHanCharacter() || text in commonSymbols
                HandwritingLanguage.ENGLISH -> englishWord.matches(text) || text in commonSymbols
            }
        }
    }

    fun isEnglishWord(text: String): Boolean = englishWord.matches(text)

    private fun String.isSingleHanCharacter(): Boolean {
        if (codePointCount(0, length) != 1) return false
        return Character.UnicodeScript.of(codePointAt(0)) == Character.UnicodeScript.HAN
    }

    private const val MaximumTextLength = 48
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FormattedText

class ChineseT9CodePresentationSource(
    private val formatText: (String) -> FormattedText?
) {
    fun build(key: ChineseT9PresentationSnapshotKey): T9PresentationState {
        key.pendingPunctuationText?.let { punctuation ->
            return T9PresentationState(
                topReading = formatText(punctuation),
                pinyinOptions = emptyList()
            )
        }
        val text = when (key.scheme) {
            ChineseT9Scheme.PINYIN -> ""
            ChineseT9Scheme.STROKE -> strokeDisplay(key.rawSequence)
            ChineseT9Scheme.ZHUYIN -> zhuyinDisplay(key)
        }
        return T9PresentationState(
            topReading = text.takeIf { it.isNotEmpty() }?.let(formatText),
            pinyinOptions = emptyList()
        )
    }

    fun rawDisplay(scheme: ChineseT9Scheme, rawCode: String): String = when (scheme) {
        ChineseT9Scheme.PINYIN -> ""
        ChineseT9Scheme.STROKE -> strokeDisplay(rawCode)
        ChineseT9Scheme.ZHUYIN -> zhuyinFallback(rawCode)
    }

    fun formattedRawDisplay(scheme: ChineseT9Scheme, rawCode: String): FormattedText? =
        rawDisplay(scheme, rawCode)
            .takeIf { it.isNotEmpty() }
            ?.let(formatText)

    private fun strokeDisplay(rawCode: String): String = buildString(rawCode.length) {
        rawCode.forEach { digit ->
            append(
                when (digit) {
                    '1' -> '一'
                    '2' -> '丨'
                    '3' -> '丿'
                    '4' -> '丶'
                    '5' -> '乛'
                    '6' -> '？'
                    else -> digit
                }
            )
        }
    }

    private fun zhuyinDisplay(key: ChineseT9PresentationSnapshotKey): String {
        val resolved = key.candidateComment
            .asSequence()
            .filter(::isZhuyinSymbol)
            .take(key.rawSequence.length)
            .joinToString("")
        if (resolved.isNotEmpty()) return resolved

        val preedit = key.inputPreedit
            .asSequence()
            .filter(::isZhuyinSymbol)
            .take(key.rawSequence.length)
            .joinToString("")
        return preedit.ifEmpty { zhuyinFallback(key.rawSequence) }
    }

    private fun zhuyinFallback(rawCode: String): String = rawCode.map { digit ->
        when (digit) {
            '\'' -> "/"
            '0' -> "ㄧㄨㄩ"
            '1' -> "ㄅㄆㄇㄈ"
            '2' -> "ㄉㄊㄋㄌ"
            '3' -> "ㄍㄎㄏ"
            '4' -> "ㄐㄑㄒ"
            '5' -> "ㄓㄔㄕㄖ"
            '6' -> "ㄗㄘㄙ"
            '7' -> "ㄚㄛㄜㄝ"
            '8' -> "ㄞㄟㄠㄡ"
            '9' -> "ㄢㄣㄤㄥㄦ"
            else -> digit.toString()
        }
    }.joinToString(" ")

    private fun isZhuyinSymbol(char: Char): Boolean =
        char in '\u3105'..'\u312F' || char in '\u31A0'..'\u31BF'
}

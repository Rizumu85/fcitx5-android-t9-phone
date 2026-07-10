/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

object ChineseT9CandidateFreshness {
    fun matches(
        data: FcitxEvent.PagedCandidateEvent.Data,
        scheme: ChineseT9Scheme,
        digitSequence: String,
        enginePreedit: String
    ): Boolean = when {
        digitSequence.isEmpty() -> true
        scheme == ChineseT9Scheme.PINYIN ->
            data.candidates.isNotEmpty() && matchesPinyin(data, digitSequence)
        scheme == ChineseT9Scheme.STROKE -> matchesStroke(digitSequence, enginePreedit)
        else -> matchesZhuyin(data, digitSequence, enginePreedit)
    }

    private fun matchesStroke(digitSequence: String, enginePreedit: String): Boolean {
        val concrete = T9StrokeCodec.digitsFromEnginePreedit(enginePreedit)
        if (concrete.length < digitSequence.length) return false
        // Runtime wildcard expansion shows one concrete branch in Rime's preedit. Match the
        // requested pattern position-by-position so a delayed unrelated page cannot release it.
        return digitSequence.indices.all { index ->
            digitSequence[index] == '6' || digitSequence[index] == concrete[index]
        }
    }

    private fun matchesPinyin(
        data: FcitxEvent.PagedCandidateEvent.Data,
        digitSequence: String
    ): Boolean {
        val digits = digitSequence.filter { it in '2'..'9' }
        return data.candidates.any { candidate ->
            candidate.comment.matchesPinyinDigits(digits) ||
                candidate.text.matchesPinyinDigits(digits)
        }
    }

    private fun String.matchesPinyinDigits(digits: String): Boolean =
        toPinyinT9Digits().startsWith(digits)

    private fun String.toPinyinT9Digits(): String =
        buildString {
            this@toPinyinT9Digits.forEach { char ->
                letterToT9Digit(char)?.let(::append)
            }
        }

    private fun matchesZhuyin(
        data: FcitxEvent.PagedCandidateEvent.Data,
        digitSequence: String,
        enginePreedit: String
    ): Boolean {
        var hasCandidateReading = false
        data.candidates.forEach { candidate ->
            val reading = zhuyinDigits(candidate.comment)
            if (reading.isNotEmpty()) {
                hasCandidateReading = true
                if (reading.startsWith(digitSequence)) return true
            }
        }
        return !hasCandidateReading &&
            enginePreedit.filter { it in '0'..'9' }.startsWith(digitSequence)
    }

    private fun zhuyinDigits(reading: String): String = buildString {
        reading.forEach { char ->
            zhuyinDigit(char)?.let(::append)
        }
    }

    private fun zhuyinDigit(char: Char): Char? = when (char) {
        in 'ㄧ'..'ㄩ' -> '0'
        in 'ㄅ'..'ㄈ' -> '1'
        in 'ㄉ'..'ㄌ' -> '2'
        in 'ㄍ'..'ㄏ' -> '3'
        in 'ㄐ'..'ㄒ' -> '4'
        in 'ㄓ'..'ㄖ' -> '5'
        in 'ㄗ'..'ㄙ' -> '6'
        in 'ㄚ'..'ㄝ' -> '7'
        in 'ㄞ'..'ㄡ' -> '8'
        in 'ㄢ'..'ㄦ' -> '9'
        else -> null
    }

    private fun letterToT9Digit(char: Char): Char? =
        when (char.lowercaseChar()) {
            in 'a'..'c' -> '2'
            in 'd'..'f' -> '3'
            in 'g'..'i' -> '4'
            in 'j'..'l' -> '5'
            in 'm'..'o' -> '6'
            in 'p'..'s' -> '7'
            in 't'..'v' -> '8'
            in 'w'..'z' -> '9'
            else -> null
        }
}

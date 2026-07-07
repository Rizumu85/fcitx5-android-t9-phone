/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

object ChineseT9CandidateFreshness {
    fun matchesDigitSequence(
        data: FcitxEvent.PagedCandidateEvent.Data,
        digitSequence: String
    ): Boolean {
        val digits = digitSequence.filter { it in '2'..'9' }
        if (digits.isEmpty()) return true
        if (data.candidates.isEmpty()) return false
        return data.candidates.any { candidate ->
            candidate.comment.matchesDigits(digits) ||
                candidate.text.matchesDigits(digits)
        }
    }

    private fun String.matchesDigits(digits: String): Boolean =
        toT9Digits().startsWith(digits)

    private fun String.toT9Digits(): String =
        buildString {
            this@toT9Digits.forEach { char ->
                letterToT9Digit(char)?.let(::append)
            }
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

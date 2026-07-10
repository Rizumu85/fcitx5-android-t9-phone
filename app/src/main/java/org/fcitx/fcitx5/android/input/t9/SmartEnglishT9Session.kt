/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class SmartEnglishT9Session(
    private val noMatchText: String
) {
    private val digits = StringBuilder()
    private var cursorIndex = 0

    val hasDigits: Boolean
        get() = digits.isNotEmpty()

    val cursor: Int
        get() = cursorIndex

    val digitSequence: String
        get() = digits.toString()

    fun appendDigit(digit: Int) {
        if (digit !in 0..9) return
        digits.append(('0'.code + digit).toChar())
        cursorIndex = 0
    }

    fun reset() {
        digits.clear()
        cursorIndex = 0
    }

    fun visibleCandidates(
        candidates: List<String>,
        showNoMatch: Boolean = true,
        transform: (String) -> String
    ): List<String> {
        val words = candidates.map(transform)
        return when {
            words.isNotEmpty() -> words
            showNoMatch -> listOf(noMatchText)
            else -> emptyList()
        }
    }

    fun inputPreviewText(candidates: List<String>): String {
        val typedLength = digits.length
        val selectedPrefix = candidates
            .getOrNull(cursorIndex)
            ?: candidates.firstOrNull()
        return selectedPrefix
            ?.take(typedLength)
            ?.takeIf { it.isNotEmpty() }
            ?: digitSkeleton()
    }

    fun moveCandidate(delta: Int, size: Int): Boolean {
        if (size <= 0) return false
        val next = (cursorIndex + delta).coerceIn(0, size - 1)
        if (next == cursorIndex) return false
        cursorIndex = next
        return true
    }

    fun setCandidateIndex(index: Int, size: Int): Boolean {
        if (index !in 0 until size) return false
        if (index == cursorIndex) return true
        cursorIndex = index
        return true
    }

    fun selectedRawCandidate(
        candidates: List<String>,
        index: Int? = null
    ): String? =
        candidates.getOrNull(index ?: cursorIndex)

    fun backspace(): Boolean {
        if (digits.isEmpty()) return false
        digits.deleteAt(digits.lastIndex)
        cursorIndex = 0
        return true
    }

    private fun digitSkeleton(): String =
        buildString(digits.length) {
            digits.forEach { digit ->
                append(
                    when (digit) {
                        '2' -> 'a'
                        '3' -> 'd'
                        '4' -> 'g'
                        '5' -> 'j'
                        '6' -> 'm'
                        '7' -> 'p'
                        '8' -> 't'
                        '9' -> 'w'
                        else -> digit
                    }
                )
            }
        }
}

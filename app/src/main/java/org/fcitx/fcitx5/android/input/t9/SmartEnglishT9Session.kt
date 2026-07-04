/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class SmartEnglishT9Session(
    private val candidateProvider: (digits: String, limit: Int) -> List<String>,
    private val candidateLimit: Int,
    private val noMatchText: String
) {
    constructor(
        dictionary: T9EnglishDictionary,
        candidateLimit: Int,
        noMatchText: String
    ) : this(
        candidateProvider = dictionary::candidatesFor,
        candidateLimit = candidateLimit,
        noMatchText = noMatchText
    )

    private val digits = StringBuilder()
    private var cursorIndex = 0

    val hasDigits: Boolean
        get() = digits.isNotEmpty()

    val cursor: Int
        get() = cursorIndex

    fun appendDigit(digit: Int) {
        if (digit !in 0..9) return
        digits.append(('0'.code + digit).toChar())
        cursorIndex = 0
    }

    fun reset() {
        digits.clear()
        cursorIndex = 0
    }

    fun rawCandidates(): List<String> =
        if (digits.isEmpty()) emptyList() else candidateProvider(digits.toString(), candidateLimit)

    fun visibleCandidates(
        candidates: List<String> = rawCandidates(),
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

    fun inputPreviewText(candidates: List<String> = rawCandidates()): String {
        val typedLength = digits.length
        val selectedPrefix = candidates
            .getOrNull(cursorIndex)
            ?: candidates.firstOrNull()
        return selectedPrefix
            ?.take(typedLength)
            ?.takeIf { it.isNotEmpty() }
            ?: digitSkeleton()
    }

    fun moveCandidate(delta: Int): Boolean {
        val size = visibleCandidates { it }.size
        if (size <= 0) return false
        cursorIndex = (cursorIndex + delta).coerceIn(0, size - 1)
        return true
    }

    fun setCandidateIndex(index: Int): Boolean {
        if (index !in visibleCandidates { it }.indices) return false
        cursorIndex = index
        return true
    }

    fun selectedRawCandidate(index: Int? = null): String? =
        rawCandidates().getOrNull(index ?: cursorIndex)

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

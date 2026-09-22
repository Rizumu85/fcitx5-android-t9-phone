/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class T9ZhuyinReadingFilterSession(
    private val resolver: T9ZhuyinResolver
) {
    private var optionCode = ""
    private var options = emptyList<String>()
    private data class Selection(val reading: String, val start: Int, val end: Int)
    private var selections = emptyList<Selection>()

    val selectedReading: String?
        get() = selections.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.reading }

    val hasInvalidReading: Boolean
        get() = optionCode.isNotEmpty() && options.isEmpty()

    fun updateRawCode(rawDigits: String) {
        if (optionCode == rawDigits) return
        val unchangedPrefix = optionCode.commonPrefixWith(rawDigits).length
        // A choice belongs to source digits, not to a particular candidate page. Appending must
        // not undo it; editing through its source span releases only that choice and its suffix.
        selections = selections.takeWhile { it.end <= unchangedPrefix }
        optionCode = rawDigits
        rebuildOptions()
    }

    fun consumePrefix(digitCount: Int) {
        selections = selections.filter { it.start >= digitCount }.map {
            it.copy(start = it.start - digitCount, end = it.end - digitCount)
        }
        optionCode = optionCode.drop(digitCount)
        rebuildOptions()
    }

    fun visibleOptions(rawDigits: String): List<String> =
        options.takeIf { optionCode == rawDigits }.orEmpty()

    fun select(rawDigits: String, reading: String): Boolean {
        val normalized = T9ZhuyinResolver.normalizeCandidateReading(reading)
        if (normalized.isEmpty() || optionCode != rawDigits || normalized !in options) return false
        var start = 0
        selections = normalized.split(' ').map { syllable ->
            val end = start + T9ZhuyinResolver.digitsForReading(syllable).length
            Selection(syllable, start, end).also { start = end }
        }
        return true
    }

    fun filterPrefixes(): List<String> = selectedReading?.let(::listOf).orEmpty()

    fun reset() {
        optionCode = ""
        options = emptyList()
        selections = emptyList()
    }

    private fun rebuildOptions() {
        options = resolver.readingOptions(optionCode, selectedReading.orEmpty())
    }
}

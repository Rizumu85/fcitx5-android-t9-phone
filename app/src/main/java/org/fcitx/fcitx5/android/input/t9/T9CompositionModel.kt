/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FormattedText

data class T9ResolvedSegment(
    val pinyin: String,
    val sourceDigits: String,
    val sourceStart: Int = 0,
) {
    val sourceEnd: Int get() = sourceStart + sourceDigits.length

    fun matchesReading(reading: String): Boolean =
        if (pinyin in initials) reading.startsWith(pinyin) else reading == pinyin

    companion object {
        private val initials = setOf(
            "b", "c", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "q",
            "r", "s", "t", "w", "x", "y", "z", "zh", "ch", "sh"
        )
    }
}

data class T9CompositionModel(
    val resolvedSegments: List<T9ResolvedSegment> = emptyList(),
    val unresolvedDigits: String = "",
    val rawPreedit: String = "",
) {
    val resolvedReading: String
        get() = resolvedSegments.joinToString(" ") { it.pinyin }

    val hasResolvedSegments: Boolean
        get() = resolvedSegments.isNotEmpty()

    val unresolvedStart: Int
        get() {
            var start = resolvedSegments.lastOrNull()?.sourceEnd ?: 0
            while (rawPreedit.getOrNull(start) == '\'') start++
            return start
        }

    fun engineInput(): String = buildString {
        var cursor = 0
        resolvedSegments.forEach { segment ->
            append(rawPreedit.substring(cursor, segment.sourceStart))
            append(segment.pinyin)
            append('\'')
            cursor = segment.sourceEnd
            // A selected reading supplies its own boundary; consume only an adjacent separator.
            if (rawPreedit.getOrNull(cursor) == '\'') cursor++
        }
        append(rawPreedit.substring(cursor))
    }
}

data class T9PresentationState(
    val topReading: FormattedText?,
    val readingOptions: List<String>,
    val reserveTopReadingRow: Boolean = false,
    val candidateStatus: T9CandidateStatus? = null,
) {
    val readingRowVisible: Boolean
        get() = readingOptions.isNotEmpty()
}

enum class T9CandidateStatus {
    NO_MATCH,
    RIME_PREPARING,
    RIME_UNAVAILABLE
}

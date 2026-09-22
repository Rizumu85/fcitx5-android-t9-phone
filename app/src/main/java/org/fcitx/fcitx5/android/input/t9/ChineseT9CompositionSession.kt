/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class ChineseT9CompositionSession {
    data class EngineProjection(val epoch: Long, val revision: Long, val input: String)

    var model = T9CompositionModel()
        private set
    var revision: Long = 0
        private set
    var epoch: Long = 0
        private set

    val hasResolvedSegments: Boolean get() = model.hasResolvedSegments
    val resolvedSegments: List<T9ResolvedSegment> get() = model.resolvedSegments
    val unresolvedDigits: String get() = model.unresolvedDigits
    val rawPreedit: String get() = model.rawPreedit

    fun clear() {
        // Clearing even an empty document invalidates work from the previous editor/session.
        epoch++
        revision++
        model = T9CompositionModel()
    }

    fun hasState(): Boolean = model.rawPreedit.isNotEmpty()

    fun appendDigit(digit: Char) {
        if (digit in '2'..'9') update(model.copy(rawPreedit = model.rawPreedit + digit))
    }

    fun appendSeparator() = update(model.copy(rawPreedit = model.rawPreedit + '\''))

    fun backspace() {
        if (!hasState()) return
        val raw = model.rawPreedit.dropLast(1)
        update(model.copy(
            rawPreedit = raw,
            resolvedSegments = model.resolvedSegments.takeWhile { it.sourceEnd <= raw.length }
        ))
    }

    fun replace(rawComposition: String) {
        clear()
        update(T9CompositionModel(rawPreedit = rawComposition.filter { it in '2'..'9' || it == '\'' }))
    }

    fun rawSequence(): String = model.rawPreedit
    fun digitSequence(): String = model.rawPreedit.filter { it in '2'..'9' }
    fun keyCount(): Int = model.rawPreedit.count { it in '2'..'9' }
    fun fullComposition(): String = model.rawPreedit
    fun currentSegment(): String = model.rawPreedit.substring(model.unresolvedStart).substringBefore('\'')

    fun shouldReopenLastResolvedSegment(): Boolean = model.hasResolvedSegments

    fun popLastResolvedSegment(): EngineProjection? {
        if (!model.hasResolvedSegments) return null
        // Undo removes a reading annotation, never reconstructs the user's original keystrokes.
        update(model.copy(resolvedSegments = model.resolvedSegments.dropLast(1)))
        return projection()
    }

    fun selectPinyin(pinyin: String): EngineProjection? {
        val segment = currentSegment()
        val reading = pinyin.lowercase()
        val length = T9PinyinUtils.matchedPrefixLength(segment, reading)
        if (length <= 0) return null
        update(model.copy(resolvedSegments = model.resolvedSegments + T9ResolvedSegment(
            pinyin = reading,
            sourceDigits = segment.take(length),
            sourceStart = model.unresolvedStart
        )))
        return projection()
    }

    fun projection(): EngineProjection = EngineProjection(epoch, revision, model.engineInput())

    fun accepts(projection: EngineProjection): Boolean = projection.epoch == epoch

    fun consumeSelectedCandidateReading(commentSegments: List<String>): String? {
        if (!hasState() || commentSegments.isEmpty()) return null
        val raw = model.rawPreedit
        var cursor = 0
        for (reading in commentSegments) {
            while (raw.getOrNull(cursor) == '\'') cursor++
            if (cursor >= raw.length) break
            val selected = model.resolvedSegments.firstOrNull { it.sourceStart == cursor }
            if (selected != null) {
                if (!selected.matchesReading(reading)) return null
                cursor = selected.sourceEnd
            } else {
                val digits = T9PinyinUtils.pinyinToT9Keys(reading)
                val available = raw.substring(cursor).substringBefore('\'')
                if (digits.isEmpty() || !available.startsWith(digits)) return null
                cursor += digits.length
            }
        }
        if (cursor == 0) return null
        while (raw.getOrNull(cursor) == '\'') cursor++
        if (cursor == raw.length) {
            clear()
        } else {
            // A prefix commit must not undo choices on the unread suffix (including initials).
            update(model.copy(
                rawPreedit = raw.substring(cursor),
                resolvedSegments = model.resolvedSegments.filter { it.sourceStart >= cursor }
                    .map { it.copy(sourceStart = it.sourceStart - cursor) }
            ))
        }
        return rawSequence()
    }

    private fun update(next: T9CompositionModel) {
        val normalized = next.copy(
            unresolvedDigits = next.rawPreedit.substring(next.unresolvedStart).filter { it in '2'..'9' }
        )
        if (model == normalized) return
        model = normalized
        revision++
    }
}

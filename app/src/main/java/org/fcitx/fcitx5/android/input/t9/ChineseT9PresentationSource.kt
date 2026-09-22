/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FormattedText

class ChineseT9PresentationSource(
    private val formatText: (String) -> FormattedText?,
    private val buildPreeditDisplay: (String) -> FormattedText?
) {
    fun build(key: ChineseT9PresentationSnapshotKey): T9PresentationState {
        key.pendingPunctuationText?.let {
            return T9PresentationState(
                topReading = formatText(it),
                readingOptions = emptyList()
            )
        }
        val candidateReading = buildCandidatePreviewReading(
            normalizedComment = normalizeCandidateComment(key.candidateComment),
            rawTyped = key.rawSequence,
            typedDigits = key.digitSequence,
            resolvedSegments = key.model.resolvedSegments
        )
            .takeIf { it.isNotEmpty() }
            ?.let { formatText(it) }
        val localPreeditReading = preeditDisplay(
            model = key.model,
            fullComposition = key.fullComposition
        )
        val rawEndsWithSeparator = key.rawSequence.lastOrNull() == '\''
        val model = key.model
        val topReading = if (model.hasResolvedSegments) {
            // A selected pinyin prefix is user intent; do not let Rime's current
            // candidate comment override that reading when composing mixed segments.
            if (rawEndsWithSeparator) {
                localPreeditReading ?: compositionModelDisplay(model) ?: candidateReading
            } else {
                candidateReading ?: compositionModelDisplay(model)
            }
                ?: localPreeditReading
        } else {
            if (rawEndsWithSeparator) {
                localPreeditReading ?: candidateReading
            } else {
                candidateReading ?: localPreeditReading
            }
                ?: preeditDisplay(
                    model = model,
                    fullComposition = key.fullComposition,
                    rawComposition = model.rawPreedit.takeIf { it.isNotEmpty() }
                )
                ?: preeditDisplay(
                    model = model,
                    fullComposition = key.fullComposition,
                    rawComposition = key.inputPreedit.takeIf { it.isNotEmpty() }
                )
        }
        return T9PresentationState(
            topReading = topReading,
            readingOptions = if (key.currentSegment.isEmpty()) {
                emptyList()
            } else {
                T9PinyinUtils.t9KeyToPinyin(key.currentSegment)
            }
        )
    }

    fun preeditDisplay(
        model: T9CompositionModel,
        fullComposition: String,
        rawComposition: String? = null
    ): FormattedText? =
        if (rawComposition != null) {
            buildPreeditDisplay(rawComposition)
        } else if (model.hasResolvedSegments) {
            compositionModelDisplay(model)
        } else {
            buildPreeditDisplay(fullComposition)
        }

    fun compositionModelDisplay(model: T9CompositionModel): FormattedText? {
        if (!model.hasResolvedSegments) return buildPreeditDisplay(model.rawPreedit)
        return formatText(buildSelectedPreview("", model.rawPreedit, model.resolvedSegments))
    }

    companion object {
        fun normalizeCandidateComment(comment: String): String =
            comment.replace('\'', ' ').trim().lowercase()

        fun buildDigitSegmentDisplay(digits: String): String {
            if (digits.isEmpty()) return ""
            val parts = mutableListOf<String>()
            var rest = digits
            while (rest.isNotEmpty()) {
                val pinyin = T9PinyinUtils.t9KeyToPinyin(rest).firstOrNull()
                val consumed = T9PinyinUtils.matchedPrefixLength(rest, pinyin)
                if (pinyin == null || consumed <= 0) {
                    parts += rest.first().toString()
                    rest = rest.drop(1)
                } else {
                    parts += pinyin
                    rest = rest.drop(consumed)
                }
            }
            return parts.joinToString(" ")
        }

        fun commentSegmentMatchesResolvedSegment(
            commentSegment: String,
            resolvedSegment: T9ResolvedSegment
        ): Boolean = resolvedSegment.matchesReading(commentSegment)

        private fun buildSelectedPreview(
            comment: String,
            raw: String,
            resolved: List<T9ResolvedSegment>
        ): String = buildString {
            val comments = comment.split(' ').filter(String::isNotEmpty)
            val matchesPrefix = resolved.indices.all { index ->
                comments.getOrNull(index)?.let(resolved[index]::matchesReading) == true
            }
            var cursor = 0
            resolved.forEach { selected ->
                append(raw.substring(cursor, selected.sourceStart))
                append(selected.pinyin)
                cursor = selected.sourceEnd
                if (raw.getOrNull(cursor) == '\'') {
                    append('\'')
                    cursor++
                } else if (cursor < raw.length) {
                    append(' ')
                }
            }
            val remaining = raw.substring(cursor)
            if (remaining.isNotEmpty()) {
                // Candidate comments explain only the unread suffix. Equal T9 digits do not
                // authorize replacing an explicitly selected spelling (ge is not he).
                val suffixComment = if (matchesPrefix) comments.drop(resolved.size).joinToString(" ") else ""
                val preview = buildCandidatePreviewReading(
                    suffixComment, remaining, remaining.filter { it in '2'..'9' }, emptyList()
                )
                append(preview.ifEmpty {
                    remaining.split('\'').joinToString("'") { buildDigitSegmentDisplay(it) }
                })
            }
        }

        private fun buildCandidatePreviewReading(
            normalizedComment: String,
            rawTyped: String,
            typedDigits: String,
            resolvedSegments: List<T9ResolvedSegment>
        ): String {
            if (resolvedSegments.isNotEmpty()) {
                return buildSelectedPreview(normalizedComment, rawTyped, resolvedSegments)
            }
            if (normalizedComment.isEmpty()) return ""
            if (rawTyped.contains('\'')) {
                return buildSeparatorAwareCandidatePreviewReading(
                    normalizedComment = normalizedComment,
                    rawTyped = rawTyped
                )
            }
            if (typedDigits.isEmpty()) return ""
            val segments = normalizedComment.split(' ').filter { it.isNotEmpty() }
            val parts = mutableListOf<String>()
            var typedIndex = 0
            var mergeNextPart = false
            segments.forEach { segment ->
                if (typedIndex >= typedDigits.length) return@forEach
                val part = StringBuilder()
                var skippedBeforeFirstMatch = false
                var skippedAfterMatch = false
                segment.forEach { char ->
                    if (typedIndex >= typedDigits.length) return@forEach
                    val digit = pinyinCharToT9Digit(char) ?: return@forEach
                    if (digit == typedDigits[typedIndex]) {
                        if (parts.isEmpty() && part.isEmpty() && skippedBeforeFirstMatch) {
                            return ""
                        }
                        part.append(char)
                        typedIndex++
                    } else if (part.isEmpty()) {
                        skippedBeforeFirstMatch = true
                    } else {
                        skippedAfterMatch = true
                    }
                }
                if (part.isNotEmpty()) {
                    if (mergeNextPart && parts.isNotEmpty()) {
                        parts[parts.lastIndex] = parts.last() + part
                    } else {
                        parts += part.toString()
                    }
                    mergeNextPart = skippedAfterMatch && typedIndex < typedDigits.length
                }
            }
            if (typedIndex < typedDigits.length) {
                buildDigitSegmentDisplay(typedDigits.drop(typedIndex))
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        if (mergeNextPart && parts.isNotEmpty()) {
                            parts[parts.lastIndex] = parts.last() + it.replace(" ", "")
                        } else {
                            parts += it
                        }
                    }
            }
            return parts.joinToString(" ")
        }

        private fun buildSeparatorAwareCandidatePreviewReading(
            normalizedComment: String,
            rawTyped: String
        ): String {
            val typedSegments = rawTyped.filter { it in '2'..'9' || it == '\'' }.split('\'')
            val commentSegments = normalizedComment.split(' ').filter { it.isNotEmpty() }
            var commentIndex = 0
            return typedSegments.joinToString("'") { digits ->
                if (digits.isEmpty()) return@joinToString ""
                val (display, nextIndex) = buildCandidatePreviewForRawSegment(
                    commentSegments, commentIndex, digits
                )
                commentIndex = nextIndex
                display ?: buildDigitSegmentDisplay(digits).replace(" ", "")
            }
        }

        private fun buildCandidatePreviewForRawSegment(
            commentSegments: List<String>,
            startIndex: Int,
            typedDigits: String
        ): Pair<String?, Int> {
            if (typedDigits.isEmpty()) return null to startIndex
            val display = StringBuilder()
            var commentIndex = startIndex
            var typedIndex = 0
            while (typedIndex < typedDigits.length && commentIndex < commentSegments.size) {
                val (part, nextTypedIndex) = matchCandidatePreviewSyllable(
                    commentSegments[commentIndex],
                    typedDigits,
                    typedIndex
                )
                if (part.isEmpty() || nextTypedIndex == typedIndex) break
                display.append(part)
                typedIndex = nextTypedIndex
                commentIndex++
            }
            if (display.isEmpty()) return null to startIndex
            if (typedIndex < typedDigits.length) {
                display.append(buildDigitSegmentDisplay(typedDigits.drop(typedIndex)).replace(" ", ""))
            }
            return display.toString() to commentIndex
        }

        private fun matchCandidatePreviewSyllable(
            commentSegment: String,
            typedDigits: String,
            startIndex: Int
        ): Pair<String, Int> {
            val part = StringBuilder()
            var typedIndex = startIndex
            var skippedBeforeFirstMatch = false
            commentSegment.forEach { char ->
                if (typedIndex >= typedDigits.length) return@forEach
                val digit = pinyinCharToT9Digit(char) ?: return@forEach
                if (digit == typedDigits[typedIndex]) {
                    if (part.isEmpty() && skippedBeforeFirstMatch) return "" to startIndex
                    part.append(char)
                    typedIndex++
                } else if (part.isEmpty()) {
                    skippedBeforeFirstMatch = true
                }
            }
            return part.toString() to typedIndex
        }

        private fun pinyinCharToT9Digit(char: Char): Char? =
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
}

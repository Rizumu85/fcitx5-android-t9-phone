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
                pinyinOptions = emptyList()
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
            pinyinOptions = if (key.currentSegment.isEmpty()) {
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
        if (!model.hasResolvedSegments && model.unresolvedDigits.isEmpty()) {
            return null
        }
        if (model.rawPreedit.contains('\'')) {
            var rawDisplay = model.rawPreedit
            model.resolvedSegments.forEach { segment ->
                rawDisplay = rawDisplay.replaceFirst(segment.sourceDigits, segment.pinyin)
            }
            return buildPreeditDisplay(rawDisplay)
        }
        val parts = model.resolvedSegments.map { it.pinyin }.toMutableList()
        if (model.unresolvedDigits.isNotEmpty()) {
            parts += T9PinyinUtils.t9KeyToPinyin(model.unresolvedDigits).firstOrNull()
                ?: model.unresolvedDigits
        }
        return formatText(parts.joinToString(" "))
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
        ): Boolean {
            if (commentSegment == resolvedSegment.pinyin) return true
            return T9PinyinUtils.pinyinToT9Keys(commentSegment) == resolvedSegment.sourceDigits
        }

        private fun buildCandidatePreviewReading(
            normalizedComment: String,
            rawTyped: String,
            typedDigits: String,
            resolvedSegments: List<T9ResolvedSegment>
        ): String {
            if (normalizedComment.isEmpty()) return ""
            if (rawTyped.contains('\'')) {
                return buildSeparatorAwareCandidatePreviewReading(
                    normalizedComment = normalizedComment,
                    rawTyped = rawTyped,
                    resolvedSegments = resolvedSegments
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
            rawTyped: String,
            resolvedSegments: List<T9ResolvedSegment>
        ): String {
            val typedSegments = rawTyped.filter { it in '2'..'9' || it == '\'' }
                .split('\'')
                .map { segment -> segment.filter { it in '2'..'9' } }
            if (typedSegments.isEmpty()) return ""
            val commentSegments = normalizedComment.split(' ').filter { it.isNotEmpty() }
            var commentIndex = 0
            var resolvedIndex = 0
            val parts = typedSegments.map { digits ->
                if (digits.isEmpty()) return@map ""
                val resolved = resolvedSegments.getOrNull(resolvedIndex)
                    ?.takeIf { digits.startsWith(it.sourceDigits) }
                val resolvedDisplay = resolved?.pinyin.orEmpty()
                var remainingDigits = digits
                if (resolved != null) {
                    remainingDigits = digits.drop(resolved.sourceDigits.length)
                    commentIndex = advancePreviewCommentIndexForResolved(
                        commentSegments,
                        commentIndex,
                        resolved
                    )
                    resolvedIndex++
                }
                if (remainingDigits.isEmpty()) {
                    resolvedDisplay
                } else {
                    val (candidateDisplay, nextCommentIndex) =
                        buildCandidatePreviewForRawSegment(
                            commentSegments,
                            commentIndex,
                            remainingDigits
                        )
                    commentIndex = nextCommentIndex
                    resolvedDisplay + (
                        candidateDisplay ?: buildDigitSegmentDisplay(remainingDigits).replace(" ", "")
                    )
                }
            }
            val display = parts.joinToString("'")
            return if (rawTyped.lastOrNull() == '\'') "$display'" else display
        }

        private fun advancePreviewCommentIndexForResolved(
            commentSegments: List<String>,
            startIndex: Int,
            resolved: T9ResolvedSegment
        ): Int {
            if (commentSegments.getOrNull(startIndex)
                    ?.let { commentSegmentMatchesResolvedSegment(it, resolved) } == true
            ) {
                return startIndex + 1
            }
            return (startIndex + 1).coerceAtMost(commentSegments.size)
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

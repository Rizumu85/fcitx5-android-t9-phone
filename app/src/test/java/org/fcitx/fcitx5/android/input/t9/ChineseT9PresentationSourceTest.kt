/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseT9PresentationSourceTest {

    @Test
    fun candidateCommentBuildsTypedPreviewAndPinyinOptions() {
        val state = source().build(
            key(
                rawSequence = "43",
                digitSequence = "43",
                currentSegment = "43",
                fullComposition = "43",
                candidateComment = "gei"
            )
        )

        assertEquals("ge", state.topReading?.toString())
        assertEquals(listOf("ge", "he", "g", "h", "i"), state.pinyinOptions)
    }

    @Test
    fun resolvedSegmentsPreferUserSelectionOverCandidateComment() {
        val state = source().build(
            key(
                rawSequence = "43'",
                digitSequence = "43",
                currentSegment = "",
                fullComposition = "ge'",
                candidateComment = "he",
                model = T9CompositionModel(
                    resolvedSegments = listOf(T9ResolvedSegment("ge", "43")),
                    rawPreedit = "43'"
                )
            )
        )

        assertEquals("ge'", state.topReading?.toString())
        assertEquals(emptyList<String>(), state.pinyinOptions)
    }

    @Test
    fun pendingPunctuationOwnsTopReadingAndSuppressesPinyinOptions() {
        val state = source().build(
            key(
                pendingPunctuationText = "。",
                rawSequence = "43",
                digitSequence = "43",
                currentSegment = "43",
                fullComposition = "43",
                candidateComment = "ge"
            )
        )

        assertEquals("。", state.topReading?.toString())
        assertEquals(emptyList<String>(), state.pinyinOptions)
    }

    private fun source(): ChineseT9PresentationSource =
        ChineseT9PresentationSource(
            formatText = ::text,
            buildPreeditDisplay = { raw -> text(preeditDisplay(raw)) }
        )

    private fun text(value: String): FormattedText? =
        if (value.isEmpty()) {
            null
        } else {
            FormattedText(arrayOf(value), intArrayOf(TextFormatFlag.NoFlag.flag), -1)
        }

    private fun preeditDisplay(raw: String): String =
        Regex("[2-9']+|[^2-9']+").findAll(raw).joinToString("") { match ->
            val token = match.value
            if (token.all { it in '2'..'9' || it == '\'' }) {
                token.split('\'').joinToString("'") {
                    ChineseT9PresentationSource.buildDigitSegmentDisplay(it)
                }
            } else {
                token
            }
        }

    private fun key(
        pendingPunctuationText: String? = null,
        rawSequence: String,
        digitSequence: String,
        currentSegment: String,
        fullComposition: String,
        candidateComment: String,
        model: T9CompositionModel = T9CompositionModel(rawPreedit = rawSequence),
        inputPreedit: String = fullComposition
    ): ChineseT9PresentationSnapshotKey =
        ChineseT9PresentationSnapshotKey(
            pendingPunctuationText = pendingPunctuationText,
            rawSequence = rawSequence,
            digitSequence = digitSequence,
            currentSegment = currentSegment,
            fullComposition = fullComposition,
            model = model,
            inputPreedit = inputPreedit,
            candidateComment = candidateComment,
            candidateCursorIndex = 0,
            sessionRevision = 0
        )
}

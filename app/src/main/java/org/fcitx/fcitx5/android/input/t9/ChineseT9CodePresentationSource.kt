/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FormattedText

class ChineseT9CodePresentationSource(
    private val formatText: (String) -> FormattedText?,
    private val zhuyinResolver: T9ZhuyinResolver
) {
    fun build(key: ChineseT9PresentationSnapshotKey): T9PresentationState {
        key.pendingPunctuationText?.let { punctuation ->
            return T9PresentationState(
                topReading = formatText(punctuation),
                readingOptions = emptyList()
            )
        }
        return when (key.scheme) {
            ChineseT9Scheme.PINYIN -> T9PresentationState(
                topReading = null,
                readingOptions = emptyList()
            )
            ChineseT9Scheme.STROKE -> T9PresentationState(
                topReading = strokeDisplay(key.rawSequence)
                    .takeIf(String::isNotEmpty)
                    ?.let(formatText),
                readingOptions = emptyList()
            )
            ChineseT9Scheme.ZHUYIN -> zhuyinPresentation(key)
        }
    }

    fun rawDisplay(scheme: ChineseT9Scheme, rawCode: String): String = when (scheme) {
        ChineseT9Scheme.PINYIN -> ""
        ChineseT9Scheme.STROKE -> strokeDisplay(rawCode)
        ChineseT9Scheme.ZHUYIN -> when (val result = zhuyinResolver.resolve(rawCode)) {
            T9ZhuyinResolver.Result.Empty -> ""
            is T9ZhuyinResolver.Result.Valid -> ""
            is T9ZhuyinResolver.Result.Invalid -> result.rawDigits
        }
    }

    fun formattedRawDisplay(scheme: ChineseT9Scheme, rawCode: String): FormattedText? =
        rawDisplay(scheme, rawCode)
            .takeIf { it.isNotEmpty() }
            ?.let(formatText)

    private fun strokeDisplay(rawCode: String): String =
        T9StrokeCodec.display(rawCode).orEmpty()

    private fun zhuyinPresentation(key: ChineseT9PresentationSnapshotKey): T9PresentationState {
        return when (val result = zhuyinResolver.resolve(key.rawSequence)) {
            T9ZhuyinResolver.Result.Empty -> T9PresentationState(
                topReading = null,
                readingOptions = emptyList()
            )
            is T9ZhuyinResolver.Result.Valid -> T9PresentationState(
                // Predictive Zhuyin follows the focused Hanzi candidate, matching TT9's default
                // interaction instead of exposing all possible transcriptions at once.
                topReading = (zhuyinCandidatePreview(key) ?: key.selectedReading)?.let(formatText),
                readingOptions = key.explicitReadingOptions
            )
            is T9ZhuyinResolver.Result.Invalid -> T9PresentationState(
                topReading = formatText(result.rawDigits),
                readingOptions = emptyList(),
                candidateStatus = T9CandidateStatus.NO_MATCH
            )
        }
    }

    private fun zhuyinCandidatePreview(key: ChineseT9PresentationSnapshotKey): String? {
        val reading = T9ZhuyinResolver.normalizeCandidateReading(key.candidateComment)
        if (reading.isNotEmpty()) {
            return reading.takeIf {
                T9ZhuyinResolver.candidateReadingMatches(key.rawSequence, key.candidateComment)
            }
        }
        return key.candidateText.takeIf(String::isNotBlank)
    }

    companion object {
        fun isZhuyinSymbol(char: Char): Boolean =
            T9ZhuyinResolver.isZhuyinSymbol(char)
    }
}

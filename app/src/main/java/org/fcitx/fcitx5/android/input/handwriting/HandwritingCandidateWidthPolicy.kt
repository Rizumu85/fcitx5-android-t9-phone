/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import kotlin.math.max

internal class HandwritingCandidateWidthPolicy(
    compactChineseWidthPx: Int,
    naturalMinimumWidthPx: Int,
    naturalHorizontalInsetPx: Int,
    viewportInsetPx: Int
) {
    private val compactChineseWidthPx = compactChineseWidthPx.coerceAtLeast(1)
    private val naturalMinimumWidthPx = naturalMinimumWidthPx.coerceAtLeast(1)
    private val naturalHorizontalInsetPx = naturalHorizontalInsetPx.coerceAtLeast(0)
    private val viewportInsetPx = viewportInsetPx.coerceAtLeast(0)

    fun resolve(
        language: HandwritingLanguage,
        candidateTexts: List<String>,
        measuredContentWidthsPx: List<Int>,
        viewportWidthPx: Int
    ): List<Int> {
        if (candidateTexts.isEmpty()) return emptyList()
        require(candidateTexts.size == measuredContentWidthsPx.size)

        val viewportWidth = viewportWidthPx.coerceAtLeast(1)
        if (language == HandwritingLanguage.CHINESE && candidateTexts.all(::isSingleCodePoint)) {
            val balancedWidth = max(compactChineseWidthPx, viewportWidth / candidateTexts.size)
            return List(candidateTexts.size) { balancedWidth }
        }

        val maximumNaturalWidth = (viewportWidth - viewportInsetPx)
            .coerceAtLeast(naturalMinimumWidthPx)
        var anyCandidateWasCapped = false
        val naturalWidths = candidateTexts.indices.map { index ->
            val minimumWidth = if (
                language == HandwritingLanguage.CHINESE &&
                isSingleCodePoint(candidateTexts[index])
            ) {
                compactChineseWidthPx
            } else {
                naturalMinimumWidthPx
            }
            val desiredWidth = measuredContentWidthsPx[index].coerceAtLeast(0) +
                naturalHorizontalInsetPx * 2
            val maximumWidth = maximumNaturalWidth.coerceAtLeast(minimumWidth)
            if (desiredWidth > maximumWidth) anyCandidateWasCapped = true
            desiredWidth.coerceIn(minimumWidth, maximumWidth)
        }

        if (
            language != HandwritingLanguage.CHINESE ||
            anyCandidateWasCapped ||
            naturalWidths.sum() >= viewportWidth
        ) {
            return naturalWidths
        }

        // Chinese recognition intentionally fills the strip. Preserve that visual rhythm for
        // short prediction pages, but use natural phrase widths once the page needs to scroll.
        val spareWidth = viewportWidth - naturalWidths.sum()
        val sharedExtra = spareWidth / naturalWidths.size
        val remainder = spareWidth % naturalWidths.size
        return naturalWidths.mapIndexed { index, width ->
            width + sharedExtra + if (index < remainder) 1 else 0
        }
    }

    private fun isSingleCodePoint(text: String): Boolean =
        text.codePointCount(0, text.length) == 1
}

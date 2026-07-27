/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertEquals
import org.junit.Test

class HandwritingCandidateWidthPolicyTest {
    private val policy = HandwritingCandidateWidthPolicy(
        compactChineseWidthPx = 34,
        naturalMinimumWidthPx = 48,
        naturalHorizontalInsetPx = 10,
        viewportInsetPx = 8
    )

    @Test
    fun multiCharacterChinesePredictionsUseNaturalWidths() {
        val widths = policy.resolve(
            language = HandwritingLanguage.CHINESE,
            candidateTexts = List(10) { "你好" },
            measuredContentWidthsPx = List(10) { 40 },
            viewportWidthPx = 320
        )

        assertEquals(List(10) { 60 }, widths)
    }

    @Test
    fun singleCharacterChineseRecognitionKeepsBalancedCells() {
        val widths = policy.resolve(
            language = HandwritingLanguage.CHINESE,
            candidateTexts = listOf("你", "好", "吗", "呢"),
            measuredContentWidthsPx = List(4) { 20 },
            viewportWidthPx = 320
        )

        assertEquals(List(4) { 80 }, widths)
    }

    @Test
    fun shortChinesePredictionPageUsesAvailableStripWidth() {
        val widths = policy.resolve(
            language = HandwritingLanguage.CHINESE,
            candidateTexts = listOf("你好", "你们", "您好"),
            measuredContentWidthsPx = List(3) { 40 },
            viewportWidthPx = 320
        )

        assertEquals(listOf(107, 107, 106), widths)
    }

    @Test
    fun oversizedCandidateLeavesViewportRevealMargin() {
        val widths = policy.resolve(
            language = HandwritingLanguage.CHINESE,
            candidateTexts = listOf("这是一个非常长的联想候选"),
            measuredContentWidthsPx = listOf(400),
            viewportWidthPx = 320
        )

        assertEquals(listOf(312), widths)
    }

    @Test
    fun englishCandidatesKeepIndependentNaturalWidths() {
        val widths = policy.resolve(
            language = HandwritingLanguage.ENGLISH,
            candidateTexts = listOf("I", "hello"),
            measuredContentWidthsPx = listOf(10, 50),
            viewportWidthPx = 320
        )

        assertEquals(listOf(48, 70), widths)
    }
}

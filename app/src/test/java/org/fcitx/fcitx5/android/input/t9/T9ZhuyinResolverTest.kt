/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9ZhuyinResolverTest {
    private val resolver = T9ZhuyinResolver()

    @Test
    fun convertsKnownPinyinSyllablesToBopomofo() {
        assertEquals("ㄋㄧ", T9ZhuyinSyllableCodec.fromPinyin("ni"))
        assertEquals("ㄏㄠ", T9ZhuyinSyllableCodec.fromPinyin("hao"))
        assertEquals("ㄓㄨㄥ", T9ZhuyinSyllableCodec.fromPinyin("zhong"))
        assertEquals("ㄒㄩㄝ", T9ZhuyinSyllableCodec.fromPinyin("xue"))
    }

    @Test
    fun resolvesSingleAndMultiSyllableDigitSequences() {
        assertTrue(resolver.resolve("38") is T9ZhuyinResolver.Result.Valid)
        assertTrue(resolver.resolve("2038") is T9ZhuyinResolver.Result.Valid)
    }

    @Test
    fun localValidityMatchesTheExhaustiveTwoKeyRimeSweep() {
        val invalid = setOf(
            "11", "12", "13", "14", "15", "16",
            "21", "22", "23", "24", "25", "26",
            "31", "32", "33", "34", "35", "36",
            "41", "42", "43", "44", "45", "46", "47", "48", "49"
        )

        for (first in '0'..'9') {
            for (second in '0'..'9') {
                val code = "$first$second"
                assertEquals(
                    "Unexpected local validity for $code",
                    code !in invalid,
                    resolver.resolve(code) is T9ZhuyinResolver.Result.Valid
                )
            }
        }
    }

    @Test
    fun impossibleConsonantSequenceProducesNoMatch() {
        assertEquals(
            T9ZhuyinResolver.Result.Invalid("33"),
            resolver.resolve("33")
        )
    }

    @Test
    fun candidateReadingNormalizesRimeBoundariesAndMatchesCompletion() {
        assertEquals(
            "ㄋㄧ ㄏㄠ",
            T9ZhuyinResolver.normalizeCandidateReading("ㄋㄧ'ㄏㄠ")
        )
        assertTrue(T9ZhuyinResolver.candidateReadingMatches("2038", "ㄋㄧ'ㄏㄠ"))
        assertTrue(T9ZhuyinResolver.candidateReadingMatches("3", "ㄏㄠ"))
        assertFalse(T9ZhuyinResolver.candidateReadingMatches("38", "ㄋㄧ"))
    }

    @Test
    fun readingOptionsAreBuiltOnlyAsLegalWholeSequenceCombinations() {
        val single = resolver.readingOptions("38", preferredReading = "ㄏㄠ")
        val phrase = resolver.readingOptions("2038", preferredReading = "ㄋㄧ'ㄏㄠ")

        assertEquals("ㄏㄠ", single.first())
        assertTrue("ㄍㄞ" in single)
        assertFalse("ㄍ" in single)
        assertEquals("ㄋㄧ ㄏㄠ", phrase.first())
        assertTrue(phrase.all { T9ZhuyinResolver.digitsForReading(it) == "2038" })
    }

    @Test
    fun focusedCandidateCompletionProjectsToTheEnteredReadingPrefix() {
        assertEquals("ㄏ", T9ZhuyinResolver.readingPrefixForDigits("3", "ㄏㄠ"))
        assertEquals("ㄋㄧ ㄏ", T9ZhuyinResolver.readingPrefixForDigits("203", "ㄋㄧ'ㄏㄠ"))
        assertEquals(null, T9ZhuyinResolver.readingPrefixForDigits("38", "ㄋㄧ"))
    }

    @Test
    fun candidateFilteringDistinguishesCompleteReadingsFromPartialFinals() {
        assertTrue(resolver.candidateMatchesReadingOption("ㄏㄠ", "ㄏ"))
        assertTrue(resolver.candidateMatchesReadingOption("ㄋㄧ'ㄏㄠ", "ㄋㄧ ㄏ"))
        assertTrue(resolver.candidateMatchesReadingOption("ㄋㄧ", "ㄋㄧ"))
        assertFalse(resolver.candidateMatchesReadingOption("ㄋㄧㄠ", "ㄋㄧ"))
        assertFalse(resolver.candidateMatchesReadingOption("ㄋㄧ'ㄍㄠ", "ㄋㄧ ㄏ"))
    }
}

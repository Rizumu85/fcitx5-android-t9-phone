/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9PinyinUtilsTest {

    @Test
    fun t9KeyToPinyinReturnsCandidatesForDigitPrefix() {
        val candidates = T9PinyinUtils.t9KeyToPinyin("64")

        assertEquals("mi", candidates.first())
        assertTrue(candidates.contains("ni"))
        assertTrue(candidates.contains("mi"))
    }

    @Test
    fun t9KeyToPinyinReturnsCurrentSegmentBeforeFallbackPrefixes() {
        assertEquals(listOf("ma", "na", "o", "m", "n"), T9PinyinUtils.t9KeyToPinyin("62"))
        assertEquals(listOf("mi", "ni", "o", "m", "n"), T9PinyinUtils.t9KeyToPinyin("64"))
    }

    @Test
    fun readingOptionsCoverEveryPinyinAbbreviationPrefix() {
        val expectedKeys = mapOf(
            "a" to "2",
            "b" to "2",
            "c" to "2",
            "d" to "3",
            "e" to "3",
            "f" to "3",
            "g" to "4",
            "h" to "4",
            "j" to "5",
            "k" to "5",
            "l" to "5",
            "m" to "6",
            "n" to "6",
            "o" to "6",
            "p" to "7",
            "q" to "7",
            "r" to "7",
            "s" to "7",
            "t" to "8",
            "w" to "9",
            "x" to "9",
            "y" to "9",
            "z" to "9",
            "zh" to "94",
            "ch" to "24",
            "sh" to "74"
        )

        expectedKeys.forEach { (prefix, keys) ->
            assertTrue(
                "$prefix should be selectable from $keys",
                prefix in T9PinyinUtils.t9KeyToPinyin(keys)
            )
        }
    }

    @Test
    fun matchedPrefixLengthUsesPinyinDigitLength() {
        assertEquals(2, T9PinyinUtils.matchedPrefixLength("642", "ni"))
        assertEquals(0, T9PinyinUtils.matchedPrefixLength("642", "hao"))
    }

    @Test
    fun pinyinToT9KeysUsesPrecomputedReverseMap() {
        assertEquals("64", T9PinyinUtils.pinyinToT9Keys("ni"))
        assertEquals("94664", T9PinyinUtils.pinyinToT9Keys("zhong"))
        assertEquals("", T9PinyinUtils.pinyinToT9Keys("not-pinyin"))
    }
}

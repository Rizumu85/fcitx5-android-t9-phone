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

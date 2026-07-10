/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
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
        val hao = resolver.resolve("38", preferredReading = "ㄏㄠ") as T9ZhuyinResolver.Result.Valid
        val niHao = resolver.resolve("2038", preferredReading = "ㄋㄧ ㄏㄠ") as T9ZhuyinResolver.Result.Valid

        assertEquals("ㄏㄠ", hao.selectedReading)
        assertEquals("ㄋㄧ ㄏㄠ", niHao.selectedReading)
        assertTrue(niHao.readingOptions.contains("ㄋㄧ ㄏㄠ"))
    }

    @Test
    fun keepsAFirstKeyAsImmediatePartialReading() {
        val result = resolver.resolve("3") as T9ZhuyinResolver.Result.Valid

        assertTrue(result.readingOptions.containsAll(listOf("ㄍ", "ㄎ", "ㄏ")))
    }

    @Test
    fun impossibleConsonantSequenceProducesNoMatch() {
        assertEquals(
            T9ZhuyinResolver.Result.Invalid("33"),
            resolver.resolve("33")
        )
    }
}

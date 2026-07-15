/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandwritingRecognitionTextPolicyTest {
    @Test
    fun acceptsHanziAndCommonPunctuation() {
        assertEquals("写", normalizeChinese("写"))
        assertEquals("，", normalizeChinese("，"))
        assertEquals("。", normalizeChinese("。"))
        assertEquals("?", normalizeChinese("?"))
        assertEquals("+", normalizeChinese("+"))
    }

    @Test
    fun rejectsLatinWordsEmojiAndUnknownSymbolSequences() {
        assertNull(normalizeChinese("a"))
        assertNull(normalizeChinese("hello"))
        assertNull(normalizeChinese("🙂"))
        assertNull(normalizeChinese("..."))
    }

    @Test
    fun englishKeepsWordsAndCommonSymbolsWithoutLeakingHanziOrEmoji() {
        assertEquals("hello", normalizeEnglish(" hello "))
        assertEquals("don't", normalizeEnglish("don't"))
        assertEquals("mother-in-law", normalizeEnglish("mother-in-law"))
        assertEquals("?", normalizeEnglish("?"))
        assertNull(normalizeEnglish("写"))
        assertNull(normalizeEnglish("🙂"))
    }

    private fun normalizeChinese(text: String) =
        HandwritingRecognitionTextPolicy.normalize(HandwritingLanguage.CHINESE, text)

    private fun normalizeEnglish(text: String) =
        HandwritingRecognitionTextPolicy.normalize(HandwritingLanguage.ENGLISH, text)
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9SchemeTest {
    @Test
    fun classifiesOnlySchemasThatOwnThePhysicalT9Contract() {
        assertEquals(ChineseT9Scheme.PINYIN, ChineseT9Scheme.fromRimeIdentityOrNull("拼音九键"))
        assertEquals(ChineseT9Scheme.PINYIN, ChineseT9Scheme.fromRimeIdentityOrNull("中文九键"))
        assertEquals(ChineseT9Scheme.STROKE, ChineseT9Scheme.fromRimeIdentityOrNull("笔画九键"))
        assertEquals(ChineseT9Scheme.STROKE, ChineseT9Scheme.fromRimeIdentityOrNull("五笔画九键"))
        assertEquals(ChineseT9Scheme.ZHUYIN, ChineseT9Scheme.fromRimeIdentityOrNull("注音九键"))
        assertNull(ChineseT9Scheme.fromRimeIdentityOrNull("雾凇拼音"))
        assertNull(ChineseT9Scheme.fromRimeIdentityOrNull(""))
        assertEquals(ChineseT9Scheme.PINYIN, ChineseT9Scheme.fromRimeIdentityOrNull("t9"))
        assertEquals(ChineseT9Scheme.STROKE, ChineseT9Scheme.fromRimeIdentityOrNull("t9_stroke"))
        assertEquals(ChineseT9Scheme.ZHUYIN, ChineseT9Scheme.fromRimeIdentityOrNull("t9_zhuyin"))
    }

    @Test
    fun schemeDigitOwnershipMatchesPhysicalKeyContract() {
        assertFalse(ChineseT9Scheme.PINYIN.acceptsCompositionDigit(1))
        assertTrue(ChineseT9Scheme.PINYIN.acceptsCompositionDigit(2))
        assertTrue(ChineseT9Scheme.STROKE.acceptsCompositionDigit(6))
        assertFalse(ChineseT9Scheme.STROKE.acceptsCompositionDigit(7))
        assertTrue(ChineseT9Scheme.ZHUYIN.acceptsCompositionDigit(0))
        assertTrue(ChineseT9Scheme.ZHUYIN.acceptsCompositionDigit(9))
        assertTrue(ChineseT9Scheme.ZHUYIN.supportsReadingFilter)
    }
}

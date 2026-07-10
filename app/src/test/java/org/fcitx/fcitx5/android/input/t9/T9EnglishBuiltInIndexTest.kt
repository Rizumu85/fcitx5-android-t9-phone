/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class T9EnglishBuiltInIndexTest {

    private val index = T9EnglishBuiltInIndex.parse(
        sequenceOf(
            "43\the:100\tif:80",
            "435\thelp:90",
            "43556\thello:120",
            "436\tgem:70"
        ),
        prefixPoolSize = 3
    )

    @Test
    fun exactLookupPreservesDictionaryOrder() {
        assertEquals(listOf("he", "if"), index.exactWords("43"))
    }

    @Test
    fun prefixLookupExcludesExactWordsAndKeepsHighestQuality() {
        assertEquals(listOf("hello", "help", "gem"), index.prefixWords("43"))
    }

    @Test
    fun repeatedPrefixLookupUsesStableResult() {
        val first = index.prefixWords("435")
        val second = index.prefixWords("435")

        assertEquals(listOf("hello"), first)
        assertSame(first, second)
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9ZhuyinReadingFilterSessionTest {
    private val session = T9ZhuyinReadingFilterSession(T9ZhuyinResolver())

    @Test
    fun rawCodePublishesLegalReadingOptionsByDefault() {
        session.updateRawCode("38")

        assertTrue("ㄏㄠ" in session.visibleOptions("38"))
        assertTrue(session.visibleOptions("38").all {
            T9ZhuyinResolver.digitsForReading(it) == "38"
        })
        assertTrue(session.visibleOptions("3").isEmpty())
    }

    @Test
    fun selectionKeepsTheRowVisibleAndPublishesOneFilterPrefix() {
        session.updateRawCode("38")

        assertTrue(session.select("38", "ㄏㄠ"))

        assertEquals(listOf("ㄏㄠ"), session.filterPrefixes())
        assertTrue("ㄏㄠ" in session.visibleOptions("38"))
    }

    @Test
    fun rawCodeMutationReplacesOptionsAndClearsSelection() {
        session.updateRawCode("38")
        session.select("38", "ㄏㄠ")

        session.updateRawCode("3")

        assertTrue(session.filterPrefixes().isEmpty())
        assertTrue(session.visibleOptions("3").isNotEmpty())
        assertTrue(session.visibleOptions("38").isEmpty())
    }

    @Test
    fun invalidRawCodePublishesNoReadingOptions() {
        session.updateRawCode("33")

        assertTrue(session.visibleOptions("33").isEmpty())
    }
}

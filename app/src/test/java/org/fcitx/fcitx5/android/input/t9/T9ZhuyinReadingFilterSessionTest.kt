/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9ZhuyinReadingFilterSessionTest {
    private val session = T9ZhuyinReadingFilterSession(T9ZhuyinResolver())

    @Test
    fun opensOnDemandAndPrioritizesTheFocusedCandidateReading() {
        assertTrue(session.canOpen("38"))
        assertTrue(session.open("38", "ㄏㄠ"))

        assertTrue(session.expanded)
        assertEquals("ㄏㄠ", session.visibleOptions("38").first())
        assertTrue(session.visibleOptions("3").isEmpty())
    }

    @Test
    fun selectionClosesTheRowAndPublishesOneFilterPrefix() {
        session.open("38", "ㄏㄠ")

        assertTrue(session.select("38", "ㄏㄠ"))

        assertFalse(session.expanded)
        assertEquals(listOf("ㄏㄠ"), session.filterPrefixes())
        assertTrue(session.visibleOptions("38").isEmpty())
    }

    @Test
    fun dismissalPreservesSelectionButResetClearsTheCompositionContract() {
        session.open("38", "ㄏㄠ")
        session.select("38", "ㄏㄠ")
        session.open("38", "ㄏㄠ")

        session.close()
        assertEquals(listOf("ㄏㄠ"), session.filterPrefixes())

        session.reset()
        assertFalse(session.expanded)
        assertTrue(session.filterPrefixes().isEmpty())
    }
}

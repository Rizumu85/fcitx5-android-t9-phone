/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingCandidateSessionTest {
    @Test
    fun pageUsesPhoneShortcutOrder() {
        val session = session(pageSize = 10)
        session.replace((0..11).map(Int::toString))

        val page = session.snapshot()

        assertEquals((0..9).map(Int::toString), page.items.map { it.text })
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), page.items.map { it.shortcutLabel })
        assertTrue(page.hasNextPage)
    }

    @Test
    fun movingAcrossBoundaryPublishesTheSelectedPage() {
        val session = session(pageSize = 4)
        session.replace((0..7).map(Int::toString))
        repeat(4) { assertTrue(session.move(1)) }

        val page = session.snapshot()

        assertEquals(1, page.pageIndex)
        assertEquals(4, page.selectedOriginalIndex)
        assertEquals(listOf("4", "5", "6", "7"), page.items.map { it.text })
    }

    @Test
    fun pageOffsetSelectsFirstCandidateOnDestinationPage() {
        val session = session(pageSize = 4)
        session.replace((0..9).map(Int::toString))

        assertTrue(session.offsetPage(1))
        assertEquals(4, session.snapshot().selectedOriginalIndex)
        assertTrue(session.offsetPage(1))
        assertEquals(8, session.snapshot().selectedOriginalIndex)
        assertFalse(session.offsetPage(1))
    }

    @Test
    fun numericShortcutUsesCurrentPage() {
        val session = session(pageSize = 4)
        session.replace((0..7).map(Int::toString))
        session.offsetPage(1)

        assertEquals(6, session.originalIndexForShortcut(2))
        assertNull(session.originalIndexForShortcut(4))
    }

    @Test
    fun pageNeverExposesMoreCandidatesThanPhysicalShortcutKeys() {
        val session = session(pageSize = 24)
        session.replace((0..14).map(Int::toString))

        assertEquals(10, session.snapshot().items.size)
    }

    private fun session(pageSize: Int) = HandwritingCandidateSession { pageSize }
}

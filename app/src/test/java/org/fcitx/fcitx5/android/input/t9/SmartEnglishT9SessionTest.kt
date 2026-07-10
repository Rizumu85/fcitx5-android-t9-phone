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

class SmartEnglishT9SessionTest {

    @Test
    fun previewShowsTypedPrefixOfSelectedCandidate() {
        val session = fakeSession()
        val candidates = listOf("hello", "help")

        session.appendDigit(4)
        session.appendDigit(3)
        session.appendDigit(5)

        assertEquals(
            listOf("hello", "help"),
            session.visibleCandidates(candidates = candidates, transform = { it })
        )
        assertEquals("hel", session.inputPreviewText(candidates))
    }

    @Test
    fun noMatchIsVisibleButNotCommittedAsRawCandidate() {
        val session = fakeSession()

        session.appendDigit(9)

        assertEquals(
            listOf("No match"),
            session.visibleCandidates(candidates = emptyList(), transform = { it })
        )
        assertEquals("w", session.inputPreviewText(emptyList()))
        assertNull(session.selectedRawCandidate(emptyList()))
    }

    @Test
    fun movingAndBackspaceKeepCursorInsideSession() {
        val session = fakeSession()
        val candidates = listOf("good", "home")

        listOf(4, 6, 6, 3).forEach(session::appendDigit)

        assertTrue(session.moveCandidate(1, candidates.size))
        assertEquals(1, session.cursor)
        assertEquals("home", session.selectedRawCandidate(candidates))

        assertTrue(session.backspace())
        assertEquals(0, session.cursor)
        assertEquals("gmm", session.inputPreviewText(emptyList()))
    }

    @Test
    fun invalidCandidateIndexIsRejected() {
        val session = fakeSession()

        session.appendDigit(4)

        assertFalse(session.setCandidateIndex(4, size = 1))
        assertEquals(0, session.cursor)
    }

    private fun fakeSession(): SmartEnglishT9Session =
        SmartEnglishT9Session(noMatchText = "No match")

}

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
        val session = fakeSession(
            "435" to listOf("hello", "help")
        )

        session.appendDigit(4)
        session.appendDigit(3)
        session.appendDigit(5)

        assertEquals(listOf("hello", "help"), session.visibleCandidates { it })
        assertEquals("hel", session.inputPreviewText())
    }

    @Test
    fun noMatchIsVisibleButNotCommittedAsRawCandidate() {
        val session = fakeSession()

        session.appendDigit(9)

        assertEquals(listOf("No match"), session.visibleCandidates { it })
        assertEquals("w", session.inputPreviewText())
        assertNull(session.selectedRawCandidate())
    }

    @Test
    fun movingAndBackspaceKeepCursorInsideSession() {
        val session = fakeSession(
            "4663" to listOf("good", "home")
        )

        listOf(4, 6, 6, 3).forEach(session::appendDigit)

        assertTrue(session.moveCandidate(1))
        assertEquals(1, session.cursor)
        assertEquals("home", session.selectedRawCandidate())

        assertTrue(session.backspace())
        assertEquals(0, session.cursor)
        assertEquals("gmm", session.inputPreviewText())
    }

    @Test
    fun invalidCandidateIndexIsRejected() {
        val session = fakeSession(
            "4" to listOf("I")
        )

        session.appendDigit(4)

        assertFalse(session.setCandidateIndex(4))
        assertEquals(0, session.cursor)
    }

    private fun fakeSession(vararg candidates: Pair<String, List<String>>): SmartEnglishT9Session {
        val candidateMap = candidates.toMap()
        return SmartEnglishT9Session(
            candidateProvider = { digits, _ -> candidateMap[digits].orEmpty() },
            candidateLimit = 80,
            noMatchText = "No match"
        )
    }

}

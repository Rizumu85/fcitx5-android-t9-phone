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

class T9PunctuationSessionTest {

    @Test
    fun chineseKeyStartsAndCyclesPendingPunctuation() {
        val session = T9PunctuationSession(
            chinesePunctuation = listOf(",", "."),
            englishPunctuation = listOf("!")
        )

        assertEquals(",", session.handleChineseKey(hasCompositionKeys = false))
        assertTrue(session.isPending)
        assertEquals(",", session.pendingText)

        assertEquals(".", session.handleChineseKey(hasCompositionKeys = false))
        assertEquals(".", session.pendingText)
    }

    @Test
    fun chineseKeyDoesNotStartPunctuationWhileCompositionHasKeys() {
        val session = T9PunctuationSession(
            chinesePunctuation = listOf(","),
            englishPunctuation = listOf("!")
        )

        assertNull(session.handleChineseKey(hasCompositionKeys = true))
        assertFalse(session.isPending)
    }

    @Test
    fun englishCandidatesCanBeSelectedAndCommitted() {
        val session = T9PunctuationSession(
            chinesePunctuation = listOf(","),
            englishPunctuation = listOf("!", "?")
        )

        assertEquals("!", session.showEnglishCandidates())
        assertEquals("?", session.selectCandidate(1))

        assertEquals("?", session.commit())
        assertFalse(session.isPending)
        assertNull(session.pendingText)
    }

    @Test
    fun deferredOneKeyPunctuationDoesNotExposePendingCandidates() {
        val session = T9PunctuationSession(
            chinesePunctuation = listOf(","),
            englishPunctuation = listOf("!")
        )

        session.deferEnglishKey()

        assertTrue(session.oneKeyDeferred)
        assertFalse(session.isPending)
        assertNull(session.paged())
    }
}

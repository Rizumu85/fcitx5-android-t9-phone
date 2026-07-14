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
    fun chineseCandidatesStartFromChineseSet() {
        val session = T9PunctuationSession(
            chinesePunctuation = listOf(",", "."),
            englishPunctuation = listOf("!")
        )

        assertEquals(",", session.showChineseCandidates())
        assertTrue(session.isPending)
        assertEquals(",", session.pendingText)
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
    fun newlineUsesVisibleLabelButCommitsLineBreak() {
        val session = T9PunctuationSession(
            chinesePunctuation = listOf("，"),
            englishPunctuation = listOf("!"),
            newlineLabel = "换行"
        )

        assertEquals("换行", session.showChineseCandidates())
        assertEquals("换行", session.paged()?.candidates?.first()?.text)
        assertEquals("\n", session.commit())
    }

}

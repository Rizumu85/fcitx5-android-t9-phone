/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateShortcutCommitterTest {

    @Test
    fun mapsPhysicalDigitKeysToCandidateShortcutIndices() {
        val calls = mutableListOf<String>()
        val committer = T9CandidateShortcutCommitter(
            commitPendingPunctuationIndex = {
                calls += "punctuation:$it"
                true
            },
            commitHanziIndex = {
                calls += "hanzi:$it"
                true
            },
            commitSmartEnglishIndex = {
                calls += "english:$it"
                true
            }
        )

        assertTrue(committer.commitPendingPunctuationKey(KeyEvent.KEYCODE_1))
        assertTrue(committer.commitHanziKey(KeyEvent.KEYCODE_9))
        assertTrue(committer.commitSmartEnglishKey(KeyEvent.KEYCODE_0))

        assertEquals(
            listOf(
                "punctuation:0",
                "hanzi:8",
                "english:9"
            ),
            calls
        )
    }

    @Test
    fun ignoresKeysWithoutCandidateShortcutIndex() {
        val calls = mutableListOf<Int>()
        val committer = T9CandidateShortcutCommitter(
            commitPendingPunctuationIndex = {
                calls += it
                true
            },
            commitHanziIndex = { false },
            commitSmartEnglishIndex = { false }
        )

        assertFalse(committer.commitPendingPunctuationKey(KeyEvent.KEYCODE_A))
        assertEquals(emptyList<Int>(), calls)
    }

    @Test
    fun returnsUnderlyingCommitResult() {
        val committer = T9CandidateShortcutCommitter(
            commitPendingPunctuationIndex = { false },
            commitHanziIndex = { false },
            commitSmartEnglishIndex = { false }
        )

        assertFalse(committer.commitPendingPunctuationKey(KeyEvent.KEYCODE_1))
        assertFalse(committer.commitHanziKey(KeyEvent.KEYCODE_2))
        assertFalse(committer.commitSmartEnglishKey(KeyEvent.KEYCODE_3))
    }
}

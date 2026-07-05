/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9MultiTapSessionTest {

    @Test
    fun repeatedKeyWithinTimeoutCyclesPendingCharacter() {
        val session = T9MultiTapSession()

        assertEquals('a', session.handleKey(KeyEvent.KEYCODE_2, 0L)?.pendingChar)
        assertEquals('b', session.handleKey(KeyEvent.KEYCODE_2, 500L)?.pendingChar)
        assertEquals('c', session.handleKey(KeyEvent.KEYCODE_2, 900L)?.pendingChar)

        assertEquals('c', session.pendingChar())
        assertTrue(session.hasPendingChar)
    }

    @Test
    fun differentKeyCommitsPreviousAndStartsNewPendingCharacter() {
        val session = T9MultiTapSession()
        session.handleKey(KeyEvent.KEYCODE_2, 0L)

        val result = session.handleKey(KeyEvent.KEYCODE_3, 200L)

        assertEquals('a', result?.committedPrevious)
        assertEquals('d', result?.pendingChar)
    }

    @Test
    fun sameKeyAfterTimeoutCommitsPreviousAndStartsAgain() {
        val session = T9MultiTapSession()
        session.handleKey(KeyEvent.KEYCODE_2, 0L)

        val result = session.handleKey(KeyEvent.KEYCODE_2, 1300L)

        assertEquals('a', result?.committedPrevious)
        assertEquals('a', result?.pendingChar)
    }

    @Test
    fun commitCancelAndResetClearPendingState() {
        val session = T9MultiTapSession()
        session.handleKey(KeyEvent.KEYCODE_2, 0L)

        assertEquals('a', session.commitPending())
        assertFalse(session.hasPendingChar)
        assertNull(session.commitPending())

        session.handleKey(KeyEvent.KEYCODE_3, 0L)
        assertTrue(session.cancelPending())
        assertFalse(session.hasPendingChar)

        session.handleKey(KeyEvent.KEYCODE_4, 0L)
        assertTrue(session.reset())
        assertFalse(session.hasPendingChar)
    }
}

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

class RimeSchemaSelectionSessionTest {
    @Test
    fun `request made during maintenance resumes before the previous active scheme`() {
        val session = RimeSchemaSelectionSession(retryDelaysMs = listOf(10L))

        assertNull(session.request(ChineseT9Scheme.ZHUYIN, engineReady = false))
        val attempt = session.resume(ChineseT9Scheme.STROKE)

        assertEquals(ChineseT9Scheme.ZHUYIN, attempt?.target)
        assertEquals(ChineseT9Scheme.ZHUYIN, session.desiredTarget)
    }

    @Test
    fun `transient failure retries and success clears the desired target`() {
        val session = RimeSchemaSelectionSession(retryDelaysMs = listOf(10L, 20L))
        val first = session.request(ChineseT9Scheme.STROKE, engineReady = true)!!

        val failure = session.onFailure(first) as RimeSchemaSelectionSession.Failure.Retry
        assertEquals(10L, failure.delayMs)
        assertEquals(1, failure.attempt.number)
        assertTrue(session.onSuccess(failure.attempt))
        assertNull(session.desiredTarget)
    }

    @Test
    fun `new target invalidates a result from the previous request`() {
        val session = RimeSchemaSelectionSession(retryDelaysMs = listOf(10L))
        val stale = session.request(ChineseT9Scheme.STROKE, engineReady = true)!!
        val current = session.request(ChineseT9Scheme.ZHUYIN, engineReady = true)!!

        assertEquals(RimeSchemaSelectionSession.Failure.Stale, session.onFailure(stale))
        assertFalse(session.onSuccess(stale))
        assertTrue(session.isCurrent(current))
    }

    @Test
    fun `engine transition invalidates work but preserves user intent`() {
        val session = RimeSchemaSelectionSession(retryDelaysMs = listOf(10L))
        val stale = session.request(ChineseT9Scheme.STROKE, engineReady = true)!!

        session.suspendForEngineTransition()

        assertFalse(session.isCurrent(stale))
        assertEquals(ChineseT9Scheme.STROKE, session.desiredTarget)
        assertEquals(ChineseT9Scheme.STROKE, session.resume(ChineseT9Scheme.PINYIN)?.target)
    }

    @Test
    fun `retry budget is bounded while retaining intent for a future engine generation`() {
        val session = RimeSchemaSelectionSession(retryDelaysMs = listOf(10L))
        val first = session.request(ChineseT9Scheme.STROKE, engineReady = true)!!
        val retry = session.onFailure(first) as RimeSchemaSelectionSession.Failure.Retry

        assertEquals(
            RimeSchemaSelectionSession.Failure.Exhausted,
            session.onFailure(retry.attempt)
        )
        assertEquals(ChineseT9Scheme.STROKE, session.desiredTarget)
        assertNull(session.resume(ChineseT9Scheme.PINYIN))

        session.suspendForEngineTransition()
        assertEquals(ChineseT9Scheme.STROKE, session.resume(ChineseT9Scheme.PINYIN)?.target)
    }

    @Test
    fun `observing the requested schema acknowledges selection without another call`() {
        val session = RimeSchemaSelectionSession(retryDelaysMs = listOf(10L))
        session.request(ChineseT9Scheme.ZHUYIN, engineReady = false)

        assertTrue(session.observeActive(ChineseT9Scheme.ZHUYIN))
        assertNull(session.desiredTarget)
        assertFalse(session.observeActive(ChineseT9Scheme.STROKE))
    }
}

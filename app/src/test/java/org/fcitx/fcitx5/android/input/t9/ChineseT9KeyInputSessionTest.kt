/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ChineseT9KeyInputSessionTest {

    @Test
    fun pressDispatchesDownAndUpInsideOneEngineOperation() = runBlocking {
        val pending = mutableListOf<suspend FakeEngine.() -> Unit>()
        val engine = FakeEngine()
        val receipt = receipt(traceInputId = 7L)
        val session = ChineseT9KeyInputSession<FakeEngine, String>(
            enqueueEngineOperation = pending::add,
            dispatchKeyStroke = { strokes += it }
        )

        session.submit(ChineseT9KeyCommand.Press("down", "up", receipt))

        assertEquals(1, pending.size)
        pending.single().invoke(engine)
        assertEquals(listOf("down", "up"), engine.strokes)
    }

    @Test
    fun commandsRemainOrderedInTheOwningEngineLane() = runBlocking {
        val pending = mutableListOf<suspend FakeEngine.() -> Unit>()
        val engine = FakeEngine()
        val session = ChineseT9KeyInputSession<FakeEngine, String>(
            enqueueEngineOperation = pending::add,
            dispatchKeyStroke = { strokes += it }
        )

        session.submit(ChineseT9KeyCommand.Stroke("first", receipt(1L)))
        session.submit(ChineseT9KeyCommand.Press("second-down", "second-up", receipt(2L)))

        pending.forEach { it.invoke(engine) }
        assertEquals(listOf("first", "second-down", "second-up"), engine.strokes)
    }

    @Test
    fun traceCallbacksKeepTheCommandReceiptIdentity() = runBlocking {
        val pending = mutableListOf<suspend FakeEngine.() -> Unit>()
        val events = mutableListOf<Pair<String, ChineseT9InputReceipt>>()
        val receipt = receipt(traceInputId = 11L)
        val session = ChineseT9KeyInputSession<FakeEngine, String>(
            enqueueEngineOperation = pending::add,
            dispatchKeyStroke = { strokes += it },
            onDispatchStarted = { events += "start" to it },
            onDispatchCompleted = { events += "complete" to it }
        )

        session.submit(ChineseT9KeyCommand.Stroke("key", receipt))
        pending.single().invoke(FakeEngine())

        assertEquals(listOf("start", "complete"), events.map { it.first })
        assertSame(receipt, events[0].second)
        assertSame(receipt, events[1].second)
    }

    private fun receipt(traceInputId: Long): ChineseT9InputReceipt =
        ChineseT9InputReceipt(
            compositionTicket = ChineseT9CompositionTicket(
                scheme = ChineseT9Scheme.PINYIN,
                rawSequence = "4",
                digitSequence = "4",
                sessionRevision = traceInputId
            ),
            traceInputId = traceInputId
        )

    private class FakeEngine {
        val strokes = mutableListOf<String>()
    }
}

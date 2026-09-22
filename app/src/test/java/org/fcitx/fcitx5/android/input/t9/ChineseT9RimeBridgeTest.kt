/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.t9

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChineseT9RimeBridgeTest {
    @Test fun replacementPreservesEveryChoiceAndExplicitBoundary() = runBlocking {
        val s = ChineseT9CompositionSession().apply { replace("64'426'62") }
        val io = MutableRimeIo(s.rawSequence())
        val bridge = ChineseT9RimeBridge(io)
        assertTrue(bridge.apply(s.selectPinyin("ni")!!))
        assertEquals("ni'426'62", io.input)
        assertTrue(bridge.apply(s.selectPinyin("hao")!!))
        assertEquals("ni'hao'62", io.input)
        assertTrue(bridge.apply(s.popLastResolvedSegment()!!))
        assertEquals("ni'426'62", io.input)
    }

    @Test fun initialBeforeSeparatorNeverConsumesNextDigit() = runBlocking {
        val s = ChineseT9CompositionSession().apply { replace("64'426") }
        val io = MutableRimeIo(s.rawSequence())
        assertTrue(ChineseT9RimeBridge(io).apply(s.selectPinyin("n")!!))
        assertEquals("n'4'426", io.input)
    }

    @Test fun failedReplacementDoesNotWeakenTheLocalChoice() = runBlocking {
        val s = ChineseT9CompositionSession().apply { replace("64") }
        val choice = s.selectPinyin("ni")!!
        val io = MutableRimeIo("99", replaceResult = false)
        assertFalse(ChineseT9RimeBridge(io).apply(choice))
        assertEquals("ni'", s.projection().input)
        assertEquals("99", io.input)
    }

    @Test fun suspendedOldOperationCannotMutateRetypedComposition() = runBlocking {
        val s = ChineseT9CompositionSession().apply { replace("64") }
        val choice = s.selectPinyin("ni")!!
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val io = MutableRimeIo("64") { entered.complete(Unit); release.await() }
        val job = launch { ChineseT9RimeBridge(io).apply(choice) }
        entered.await()
        s.clear()
        "64".forEach(s::appendDigit)
        val newChoice = s.selectPinyin("ni")!!
        release.complete(Unit)
        job.join()
        assertEquals(newChoice, s.projection())
        assertFalse(s.accepts(choice))
    }

    @Test fun queuedChoiceExecutesBeforeLaterDigitWithoutFullRevisionCancellation() = runBlocking {
        val s = ChineseT9CompositionSession().apply { replace("64") }
        val choice = s.selectPinyin("ni")!!
        val queued = mutableListOf<suspend MutableRimeIo.() -> Unit>()
        val lane = ChineseT9EngineOperation<MutableRimeIo>(queued::add, ownerDispatcher = Dispatchers.Unconfined)
        val io = MutableRimeIo("64")
        lane.enqueue(
            acceptBefore = { s.accepts(choice) },
            execute = { ChineseT9RimeBridge(this).apply(choice) },
            acceptAfter = { s.accepts(choice) },
            finish = { assertTrue(it) }
        )
        s.appendDigit('4')
        lane.enqueue { input += "4" }
        queued.forEach { it(io) }
        assertEquals("ni'4", io.input)
        assertEquals("644", s.rawSequence())
    }

    private class MutableRimeIo(
        var input: String,
        val replaceResult: Boolean = true,
        val beforeReplace: suspend () -> Unit = {}
    ) : ChineseT9RimeBridge.RimeIo {
        override suspend fun getInput(): String = input
        override suspend fun replaceInput(start: Int, length: Int, text: String, caretPos: Int): Boolean {
            beforeReplace()
            if (!replaceResult) return false
            input = input.replaceRange(start, start + length, text)
            assertEquals(input.length, caretPos)
            return true
        }
    }
}

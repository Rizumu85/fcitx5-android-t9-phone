/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseT9EngineOperationTest {

    @Test
    fun staleRequestIsRejectedBeforeEngineExecution() = runBlocking {
        val engine = FakeEngine()
        var current = false
        val pending = mutableListOf<suspend FakeEngine.() -> Unit>()
        val operation = ChineseT9EngineOperation<FakeEngine>(
            submit = pending::add,
            ownerDispatcher = Dispatchers.Unconfined
        )
        val finished = mutableListOf<String>()

        operation.enqueue(
            acceptBefore = { current },
            execute = { calls += 1; "selected" },
            finish = finished::add
        )

        pending.single().invoke(engine)

        assertEquals(0, engine.calls)
        assertEquals(emptyList<String>(), finished)
    }

    @Test
    fun requestIsRevalidatedAfterSerializedEngineExecution() = runBlocking {
        val engine = FakeEngine()
        var current = true
        val pending = mutableListOf<suspend FakeEngine.() -> Unit>()
        val operation = ChineseT9EngineOperation<FakeEngine>(
            submit = pending::add,
            ownerDispatcher = Dispatchers.Unconfined
        )
        val finished = mutableListOf<String>()

        operation.enqueue(
            acceptBefore = { current },
            execute = {
                calls += 1
                current = false
                "selected"
            },
            acceptAfter = { current },
            finish = finished::add
        )

        pending.single().invoke(engine)

        assertEquals(1, engine.calls)
        assertEquals(emptyList<String>(), finished)
    }

    private class FakeEngine {
        var calls = 0
    }
}

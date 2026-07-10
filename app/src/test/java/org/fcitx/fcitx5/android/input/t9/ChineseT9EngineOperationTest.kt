/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9EngineOperationTest {

    @Test
    fun operationWaitsForOwnerAvailabilityAndRevalidatesBeforeExecution() = runBlocking {
        val engine = FakeEngine()
        var ownerReady = false
        var current = true
        val submitted = mutableListOf<suspend FakeEngine.() -> Unit>()
        val operation = ChineseT9EngineOperation<FakeEngine>(
            submit = submitted::add,
            ownerAvailable = { ownerReady },
            ownerDispatcher = Dispatchers.Unconfined
        )

        operation.enqueue(
            acceptBefore = { current },
            execute = { calls += 1 },
            finish = {}
        )

        assertEquals(1, operation.pendingCount)
        assertTrue(submitted.isEmpty())

        current = false
        ownerReady = true
        operation.onAvailabilityChanged(true)
        submitted.single().invoke(engine)

        assertEquals(0, engine.calls)
    }

    @Test
    fun engineSideReadinessRaceReturnsOperationToPendingQueue() = runBlocking {
        val engine = FakeEngine()
        var engineReady = false
        val submitted = mutableListOf<suspend FakeEngine.() -> Unit>()
        val operation = ChineseT9EngineOperation<FakeEngine>(
            submit = submitted::add,
            ownerAvailable = { true },
            engineAvailable = { engineReady },
            ownerDispatcher = Dispatchers.Unconfined
        )

        operation.enqueue { calls += 1 }
        submitted.removeFirst().invoke(engine)

        assertEquals(0, engine.calls)
        assertEquals(1, operation.pendingCount)

        engineReady = true
        operation.onAvailabilityChanged(true)
        submitted.single().invoke(engine)

        assertEquals(1, engine.calls)
        assertEquals(0, operation.pendingCount)
    }

    @Test
    fun boundedPendingQueueDropsTheWholeUnreplayableBatch() {
        var dropped = 0
        val operation = ChineseT9EngineOperation<FakeEngine>(
            submit = {},
            ownerAvailable = { false },
            onPendingDropped = { dropped += 1 },
            maxPendingOperations = 2,
            ownerDispatcher = Dispatchers.Unconfined
        )

        operation.enqueue { calls += 1 }
        operation.enqueue { calls += 1 }
        operation.enqueue { calls += 1 }

        assertEquals(1, dropped)
        assertEquals(0, operation.pendingCount)
    }

    @Test
    fun failedAvailabilityRejectsInsteadOfQueuingForever() {
        var dropped = 0
        val operation = ChineseT9EngineOperation<FakeEngine>(
            submit = {},
            ownerAvailable = { false },
            ownerWaiting = { false },
            onPendingDropped = { dropped += 1 },
            ownerDispatcher = Dispatchers.Unconfined
        )

        operation.enqueue { calls += 1 }

        assertEquals(1, dropped)
        assertEquals(0, operation.pendingCount)
    }

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

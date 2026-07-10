/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChineseT9EngineOperation<Engine>(
    private val submit: (suspend Engine.() -> Unit) -> Unit,
    private val ownerAvailable: () -> Boolean = { true },
    private val ownerWaiting: () -> Boolean = { !ownerAvailable() },
    private val engineAvailable: suspend Engine.() -> Boolean = { true },
    private val onPendingDropped: () -> Unit = {},
    private val maxPendingOperations: Int = 256,
    private val ownerDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val pending = ArrayDeque<suspend Engine.() -> Unit>()

    fun enqueue(execute: suspend Engine.() -> Unit) {
        schedule(execute)
    }

    fun <Result> enqueue(
        acceptBefore: () -> Boolean,
        execute: suspend Engine.() -> Result,
        acceptAfter: (Result) -> Boolean = { true },
        finish: (Result) -> Unit
    ) {
        schedule {
            if (withContext(ownerDispatcher) { acceptBefore() }) {
                val result = execute()
                withContext(ownerDispatcher) {
                    if (acceptAfter(result)) finish(result)
                }
            }
        }
    }

    fun onAvailabilityChanged(available: Boolean) {
        if (!available || pending.isEmpty()) return
        // Drain a stable batch because an engine-side readiness recheck may put an operation back.
        val queued = pending.toList()
        pending.clear()
        queued.forEach(::dispatch)
    }

    fun discardPending() {
        pending.clear()
    }

    internal val pendingCount: Int
        get() = pending.size

    private fun schedule(operation: suspend Engine.() -> Unit) {
        if (ownerAvailable()) {
            dispatch(operation)
        } else if (ownerWaiting()) {
            hold(operation)
        } else {
            onPendingDropped()
        }
    }

    private fun dispatch(operation: suspend Engine.() -> Unit) {
        submit {
            if (!engineAvailable()) {
                withContext(ownerDispatcher) { hold(operation) }
                return@submit
            }
            operation()
        }
    }

    private fun hold(operation: suspend Engine.() -> Unit) {
        if (pending.size >= maxPendingOperations.coerceAtLeast(1)) {
            pending.clear()
            onPendingDropped()
            return
        }
        pending.addLast(operation)
    }
}

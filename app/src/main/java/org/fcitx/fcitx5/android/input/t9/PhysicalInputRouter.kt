/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

class PhysicalInputRouter(
    private val mapInput: (keyCode: Int, event: KeyEvent) -> Pair<Int, KeyEvent> = { keyCode, event ->
        keyCode to event
    },
    private val keyDownRoutes: List<Route>,
    private val keyUpBeforePairingRoutes: List<Route>,
    private val keyUpAfterPairingRoutes: List<Route>
) {
    class Input internal constructor(
        val keyCode: Int,
        val event: KeyEvent,
        mapInput: (keyCode: Int, event: KeyEvent) -> Pair<Int, KeyEvent>
    ) {
        private val mapped by lazy(LazyThreadSafetyMode.NONE) {
            mapInput(keyCode, event)
        }

        val mappedKeyCode: Int
            get() = mapped.first

        val mappedEvent: KeyEvent
            get() = mapped.second
    }

    data class Result(
        val handled: Boolean,
        val consumeKeyUp: Int? = null,
        val tracePath: String? = null
    )

    fun interface Route {
        fun handle(input: Input): Result?
    }

    private var consumedKeyUp: Int? = null

    fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val startedNanos = T9ResponsivenessTrace.captureInputStartNanos()
        val activeTraceAtStart = T9ResponsivenessTrace.activeInputId()
        val result = runRoutes(keyDownRoutes, Input(keyCode, event, mapInput))
        recordOwnedRouteEffect(result, startedNanos, activeTraceAtStart)
        return result?.handled == true
    }

    fun handleKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val startedNanos = T9ResponsivenessTrace.captureInputStartNanos()
        val activeTraceAtStart = T9ResponsivenessTrace.activeInputId()
        val input = Input(keyCode, event, mapInput)
        val beforePairing = runRoutes(keyUpBeforePairingRoutes, input)
        if (beforePairing?.handled == true) {
            if (consumedKeyUp == input.keyCode) consumedKeyUp = null
            recordOwnedRouteEffect(beforePairing, startedNanos, activeTraceAtStart)
            return true
        }
        if (consumedKeyUp == input.keyCode) {
            consumedKeyUp = null
            return true
        }
        val afterPairing = runRoutes(keyUpAfterPairingRoutes, input)
        recordOwnedRouteEffect(afterPairing, startedNanos, activeTraceAtStart)
        return afterPairing?.handled == true
    }

    fun reset() {
        consumedKeyUp = null
    }

    private fun runRoutes(routes: List<Route>, input: Input): Result? {
        routes.forEach { route ->
            val result = route.handle(input) ?: return@forEach
            result.consumeKeyUp?.let { consumedKeyUp = it }
            if (result.handled) return result
        }
        return null
    }

    private fun recordOwnedRouteEffect(
        result: Result?,
        startedNanos: Long?,
        activeTraceAtStart: Long?
    ) {
        val path = result?.tracePath ?: return
        // A T9 route may start an asynchronous candidate transaction before the mapped-key
        // fallback handles the same down event. That generation, not the fallback route, owns it.
        if (T9ResponsivenessTrace.activeInputId() != activeTraceAtStart) return
        T9ResponsivenessTrace.recordCompletedEffect(path, startedNanos)
    }
}

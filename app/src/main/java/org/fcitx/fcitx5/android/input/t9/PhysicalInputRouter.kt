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
    private val ownsBackKey: () -> Boolean = { true },
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
        val tracePath: String? = null,
        /**
         * The route consumed the first down of a transient surface that may close on it. Repeats
         * of the held key then belong to nobody and must not leak into later routes.
         */
        val ownsGesture: Boolean = false
    )

    fun interface Route {
        fun handle(input: Input): Result?
    }

    // Several keys can be held at once; each consumed down owns its own release.
    private val consumedKeyUps = mutableSetOf<Int>()
    private var backKeyOwnedByIme: Boolean? = null
    private var gestureOwnedKeyCode: Int? = null

    fun handleKeyDown(keyCode: Int, event: KeyEvent, repeatCount: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val visible = ownsBackKey()
            if (repeatCount == 0 || backKeyOwnedByIme == null) {
                backKeyOwnedByIme = visible
            }
            if (backKeyOwnedByIme == false) return false
            // A held Back may hide the panel. Consume the rest of that gesture without deleting
            // more text; the next press belongs to the app even if its editor still has focus.
            if (!visible) return true
        }
        if (repeatCount > 0 && gestureOwnedKeyCode == keyCode) return true
        if (repeatCount == 0 && gestureOwnedKeyCode == keyCode) gestureOwnedKeyCode = null
        val startedNanos = T9ResponsivenessTrace.captureInputStartNanos()
        val activeTraceAtStart = T9ResponsivenessTrace.activeInputId()
        val result = runRoutes(keyDownRoutes, Input(keyCode, event, mapInput), claimsRelease = true)
        recordOwnedRouteEffect(result, startedNanos, activeTraceAtStart)
        if (repeatCount == 0 && result?.handled == true && result.ownsGesture) {
            gestureOwnedKeyCode = keyCode
        }
        return result?.handled == true
    }

    fun handleKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (gestureOwnedKeyCode == keyCode) gestureOwnedKeyCode = null
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val visible = ownsBackKey()
            val owned = backKeyOwnedByIme ?: visible
            backKeyOwnedByIme = null
            if (!owned || !visible) {
                consumedKeyUps -= keyCode
                return owned
            }
        }
        val startedNanos = T9ResponsivenessTrace.captureInputStartNanos()
        val activeTraceAtStart = T9ResponsivenessTrace.activeInputId()
        val input = Input(keyCode, event, mapInput)
        val beforePairing = runRoutes(keyUpBeforePairingRoutes, input)
        if (beforePairing?.handled == true) {
            consumedKeyUps -= input.keyCode
            recordOwnedRouteEffect(beforePairing, startedNanos, activeTraceAtStart)
            return true
        }
        if (consumedKeyUps.remove(input.keyCode)) {
            return true
        }
        val afterPairing = runRoutes(keyUpAfterPairingRoutes, input)
        recordOwnedRouteEffect(afterPairing, startedNanos, activeTraceAtStart)
        return afterPairing?.handled == true
    }

    fun reset() {
        consumedKeyUps.clear()
        gestureOwnedKeyCode = null
        // Composition/panel resets do not transfer an in-flight Back release to another owner.
    }

    // Only a down can claim a future release; a claim made while releasing would linger.
    private fun runRoutes(routes: List<Route>, input: Input, claimsRelease: Boolean = false): Result? {
        routes.forEach { route ->
            val result = route.handle(input) ?: return@forEach
            if (claimsRelease) {
                result.consumeKeyUp?.let { consumedKeyUps += it }
            }
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

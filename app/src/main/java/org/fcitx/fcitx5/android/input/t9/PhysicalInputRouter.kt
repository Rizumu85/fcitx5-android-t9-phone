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
        val consumeKeyUp: Int? = null
    )

    fun interface Route {
        fun handle(input: Input): Result?
    }

    private var consumedKeyUp: Int? = null

    fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        runRoutes(keyDownRoutes, Input(keyCode, event, mapInput))

    fun handleKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val input = Input(keyCode, event, mapInput)
        if (runRoutes(keyUpBeforePairingRoutes, input)) {
            if (consumedKeyUp == input.keyCode) consumedKeyUp = null
            return true
        }
        if (consumedKeyUp == input.keyCode) {
            consumedKeyUp = null
            return true
        }
        return runRoutes(keyUpAfterPairingRoutes, input)
    }

    fun reset() {
        consumedKeyUp = null
    }

    private fun runRoutes(routes: List<Route>, input: Input): Boolean {
        routes.forEach { route ->
            val result = route.handle(input) ?: return@forEach
            result.consumeKeyUp?.let { consumedKeyUp = it }
            if (result.handled) return true
        }
        return false
    }
}

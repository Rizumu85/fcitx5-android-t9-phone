/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalInputRouterTest {

    @Test
    fun firstHandledKeyDownRouteWins() {
        val calls = mutableListOf<String>()
        val router = PhysicalInputRouter(
            keyDownRoutes = listOf(
                route(calls, "panel", handled = false),
                route(calls, "password", handled = true),
                route(calls, "t9", handled = true)
            ),
            keyUpBeforePairingRoutes = emptyList(),
            keyUpAfterPairingRoutes = emptyList()
        )

        assertTrue(handleDown(router))
        assertEquals(listOf("panel", "password"), calls)
    }

    @Test
    fun prePairingKeyUpRouteRunsBeforeConsumedKeyUp() {
        val calls = mutableListOf<String>()
        val keyCode = KeyEvent.KEYCODE_DPAD_CENTER
        val router = PhysicalInputRouter(
            keyDownRoutes = listOf(PhysicalInputRouter.Route {
                PhysicalInputRouter.Result(handled = true, consumeKeyUp = keyCode)
            }),
            keyUpBeforePairingRoutes = listOf(route(calls, "selection-release", handled = true)),
            keyUpAfterPairingRoutes = listOf(route(calls, "t9-release", handled = true))
        )

        handleDown(router, keyCode)

        assertTrue(handleUp(router, keyCode))
        assertEquals(listOf("selection-release"), calls)
    }

    @Test
    fun consumedKeyUpDoesNotReachLaterRoutes() {
        val calls = mutableListOf<String>()
        val keyCode = KeyEvent.KEYCODE_DPAD_RIGHT
        val router = PhysicalInputRouter(
            keyDownRoutes = listOf(PhysicalInputRouter.Route {
                PhysicalInputRouter.Result(handled = true, consumeKeyUp = keyCode)
            }),
            keyUpBeforePairingRoutes = emptyList(),
            keyUpAfterPairingRoutes = listOf(route(calls, "fallback", handled = true))
        )

        handleDown(router, keyCode)

        assertTrue(handleUp(router, keyCode))
        assertTrue(calls.isEmpty())
        assertTrue(handleUp(router, keyCode))
        assertEquals(listOf("fallback"), calls)
    }

    @Test
    fun mappingIsDeferredUntilARouteNeedsMappedInput() {
        var mappingCount = 0
        val router = PhysicalInputRouter(
            mapInput = { keyCode, event ->
                mappingCount += 1
                keyCode to event
            },
            keyDownRoutes = listOf(PhysicalInputRouter.Route {
                PhysicalInputRouter.Result(handled = true)
            }),
            keyUpBeforePairingRoutes = emptyList(),
            keyUpAfterPairingRoutes = emptyList()
        )

        handleDown(router)

        assertEquals(0, mappingCount)
    }

    private fun route(
        calls: MutableList<String>,
        name: String,
        handled: Boolean
    ) = PhysicalInputRouter.Route {
        calls += name
        PhysicalInputRouter.Result(handled = handled)
    }

    private fun handleDown(
        router: PhysicalInputRouter,
        keyCode: Int = KeyEvent.KEYCODE_2
    ): Boolean = router.handleKeyDown(keyCode, event(KeyEvent.ACTION_DOWN, keyCode))

    private fun handleUp(
        router: PhysicalInputRouter,
        keyCode: Int = KeyEvent.KEYCODE_2
    ): Boolean = router.handleKeyUp(keyCode, event(KeyEvent.ACTION_UP, keyCode))

    private fun event(action: Int, keyCode: Int): KeyEvent {
        val event = KeyEvent(0, 0, action, keyCode, 0)
        return event
    }
}

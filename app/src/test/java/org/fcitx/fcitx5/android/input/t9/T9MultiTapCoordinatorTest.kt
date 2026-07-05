/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9MultiTapCoordinatorTest {

    @Test
    fun handleKeyShowsComposingTextAndSchedulesCommit() {
        val scheduler = FakeScheduler()
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            scheduler = scheduler,
            events = events
        )

        assertTrue(coordinator.handleKey(KeyEvent.KEYCODE_2))

        assertEquals(listOf("compose:a"), events)
        assertTrue(coordinator.hasPendingChar)
        assertEquals("a", coordinator.pendingDisplayText())

        scheduler.runPending()

        assertEquals(listOf("compose:a", "commit:a", "learn:a", "shift"), events)
        assertFalse(coordinator.hasPendingChar)
    }

    @Test
    fun differentKeyCommitsPreviousBeforeShowingNewPending() {
        val scheduler = FakeScheduler()
        var now = 0L
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            scheduler = scheduler,
            events = events,
            nowMillis = { now }
        )

        coordinator.handleKey(KeyEvent.KEYCODE_2)
        now = 100L
        coordinator.handleKey(KeyEvent.KEYCODE_3)

        assertEquals(
            listOf(
                "compose:a",
                "commit:a",
                "learn:a",
                "shift",
                "compose:d"
            ),
            events
        )
    }

    @Test
    fun cancelAndResetClearPendingComposition() {
        val scheduler = FakeScheduler()
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            scheduler = scheduler,
            events = events
        )

        coordinator.handleKey(KeyEvent.KEYCODE_2)
        assertTrue(coordinator.cancelPending())
        coordinator.handleKey(KeyEvent.KEYCODE_3)
        assertTrue(coordinator.reset())

        assertEquals(
            listOf(
                "compose:a",
                "compose:",
                "finish",
                "compose:d",
                "finish"
            ),
            events
        )
    }

    private fun coordinator(
        scheduler: FakeScheduler,
        events: MutableList<String>,
        nowMillis: () -> Long = { 0L }
    ): T9MultiTapCoordinator =
        T9MultiTapCoordinator(
            timeoutScheduler = scheduler,
            nowMillis = nowMillis,
            commitText = { events += "commit:$it" },
            setComposingText = { events += "compose:$it" },
            finishComposingText = { events += "finish" },
            applyCase = { it },
            consumeShiftOnce = { events += "shift" },
            recordLearningChar = { events += "learn:$it" }
        )

    private class FakeScheduler : T9MultiTapCoordinator.TimeoutScheduler {
        private var pending: (() -> Unit)? = null

        override fun cancel() {
            pending = null
        }

        override fun schedule(delayMillis: Long, action: () -> Unit) {
            pending = action
        }

        fun runPending() {
            pending?.invoke()
            pending = null
        }
    }
}

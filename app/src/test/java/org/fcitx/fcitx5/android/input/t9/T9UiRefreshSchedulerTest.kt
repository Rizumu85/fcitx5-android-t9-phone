/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9UiRefreshSchedulerTest {

    @Test
    fun repeatedRequestsBeforeFrameRunOnlyRefreshOnce() {
        val posted = mutableListOf<() -> Unit>()
        var refreshCount = 0
        val scheduler = T9UiRefreshScheduler(
            postRefresh = { posted += it },
            refreshNow = { refreshCount += 1 }
        )

        scheduler.requestRefresh()
        scheduler.requestRefresh()
        scheduler.requestRefresh()

        assertEquals(1, posted.size)
        assertEquals(0, refreshCount)

        posted.single().invoke()

        assertEquals(1, refreshCount)
    }

    @Test
    fun immediateRefreshCancelsPendingFrameRefresh() {
        val posted = mutableListOf<() -> Unit>()
        var refreshCount = 0
        val scheduler = T9UiRefreshScheduler(
            postRefresh = { posted += it },
            refreshNow = { refreshCount += 1 }
        )

        scheduler.requestRefresh()
        scheduler.refreshImmediately()
        posted.single().invoke()

        assertEquals(1, refreshCount)
    }

    @Test
    fun cancelDropsPendingRefresh() {
        val posted = mutableListOf<() -> Unit>()
        var refreshCount = 0
        val scheduler = T9UiRefreshScheduler(
            postRefresh = { posted += it },
            refreshNow = { refreshCount += 1 }
        )

        scheduler.requestRefresh()
        scheduler.cancel()
        posted.single().invoke()

        assertEquals(0, refreshCount)
    }
}

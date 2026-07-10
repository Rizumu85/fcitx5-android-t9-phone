/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core.performance

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartupPerformanceTraceTest {
    private var now = 1_000L

    @Before
    fun setUp() {
        StartupPerformanceTrace.startForTest(
            generation = 7L,
            processStartNanos = 100L,
            enabled = true,
            nanoTime = { now }
        )
    }

    @After
    fun tearDown() {
        StartupPerformanceTrace.resetForTest()
    }

    @Test
    fun recordsFirstPairedStageOnly() {
        val first = StartupPerformanceTrace.beginStage(
            StartupPerformanceTrace.Stage.DATA_INSTALLATION
        )
        now = 1_500L
        StartupPerformanceTrace.endStage(first)

        val duplicate = StartupPerformanceTrace.beginStage(
            StartupPerformanceTrace.Stage.DATA_INSTALLATION
        )
        assertNull(duplicate)

        val timing = requireNotNull(
            StartupPerformanceTrace.latestSnapshot()
                ?.stage(StartupPerformanceTrace.Stage.DATA_INSTALLATION)
        )
        assertEquals(900L, timing.startOffsetNanos)
        assertEquals(500L, timing.durationNanos)
    }

    @Test
    fun firstSurfaceMayPrecedeRimeWithoutCompletingTransaction() {
        markBaseMilestones(includeRime = false)

        val partial = requireNotNull(StartupPerformanceTrace.latestSnapshot())
        assertFalse(partial.complete)

        now = 2_000L
        StartupPerformanceTrace.mark(StartupPerformanceTrace.Milestone.RIME_READY)
        assertTrue(requireNotNull(StartupPerformanceTrace.latestSnapshot()).complete)
    }

    @Test
    fun tracePreferenceGatesPublicationWithoutDiscardingCapturedHistory() {
        val token = StartupPerformanceTrace.beginStage(
            StartupPerformanceTrace.Stage.APPLICATION_CREATE
        )
        now = 1_250L
        StartupPerformanceTrace.endStage(token)

        StartupPerformanceTrace.configure(false)
        assertNull(StartupPerformanceTrace.latestSnapshot())

        StartupPerformanceTrace.configure(true)
        assertEquals(
            250L,
            StartupPerformanceTrace.latestSnapshot()
                ?.stage(StartupPerformanceTrace.Stage.APPLICATION_CREATE)
                ?.durationNanos
        )
    }

    @Test
    fun staleTokenCannotWriteIntoNewProcessGeneration() {
        val stale = StartupPerformanceTrace.beginStage(
            StartupPerformanceTrace.Stage.FCITX_NATIVE_STARTUP
        )
        StartupPerformanceTrace.startForTest(
            generation = 8L,
            processStartNanos = 1_500L,
            enabled = true,
            nanoTime = { now }
        )

        now = 3_000L
        StartupPerformanceTrace.endStage(stale)

        assertNull(
            StartupPerformanceTrace.latestSnapshot()
                ?.stage(StartupPerformanceTrace.Stage.FCITX_NATIVE_STARTUP)
        )
    }

    private fun markBaseMilestones(includeRime: Boolean) {
        listOf(
            StartupPerformanceTrace.Milestone.APPLICATION_CREATED,
            StartupPerformanceTrace.Milestone.FCITX_READY,
            StartupPerformanceTrace.Milestone.INPUT_VIEW_CREATED,
            StartupPerformanceTrace.Milestone.FIRST_INPUT_SURFACE_FRAME
        ).forEach { milestone ->
            now += 100L
            StartupPerformanceTrace.mark(milestone)
        }
        if (includeRime) {
            now += 100L
            StartupPerformanceTrace.mark(StartupPerformanceTrace.Milestone.RIME_READY)
        }
    }
}

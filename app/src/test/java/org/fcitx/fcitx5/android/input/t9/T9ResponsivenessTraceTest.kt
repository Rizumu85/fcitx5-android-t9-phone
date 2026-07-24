/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9ResponsivenessTraceTest {

    @After
    fun tearDown() {
        T9ResponsivenessTrace.configure(enabled = false)
    }

    @Test
    fun disabledTraceDoesNotRecordSamples() {
        T9ResponsivenessTrace.configure(enabled = false, aggregationWindow = 1)

        val summary = T9ResponsivenessTrace.record("section", 3_000_000L)

        assertNull(summary)
    }

    @Test
    fun enabledTraceAggregatesBySectionWindow() {
        T9ResponsivenessTrace.configure(
            enabled = true,
            slowThresholdNanos = 2_000_000L,
            aggregationWindow = 3
        )

        assertNull(T9ResponsivenessTrace.record("section", 1_000_000L))
        assertNull(T9ResponsivenessTrace.record("section", 2_000_000L))
        val summary = T9ResponsivenessTrace.record("section", 4_000_000L)!!

        assertEquals("section", summary.section)
        assertEquals(3, summary.count)
        assertEquals(2, summary.slowCount)
        assertEquals(7_000_000L, summary.totalNanos)
        assertEquals(1_000_000L, summary.minNanos)
        assertEquals(4_000_000L, summary.maxNanos)
        assertEquals(2_333_333L, summary.averageNanos)
    }

    @Test
    fun summaryFlushResetsSectionWindow() {
        T9ResponsivenessTrace.configure(enabled = true, aggregationWindow = 2)

        T9ResponsivenessTrace.record("section", 1_000_000L)
        T9ResponsivenessTrace.record("section", 1_000_000L)
        assertNull(T9ResponsivenessTrace.record("section", 1_000_000L))
    }

    @Test
    fun latestSummariesStoresFlushedWindows() {
        T9ResponsivenessTrace.configure(enabled = true, aggregationWindow = 2)

        T9ResponsivenessTrace.record("section", 1_000_000L)
        T9ResponsivenessTrace.record("section", 3_000_000L)

        val summaries = T9ResponsivenessTrace.latestSummaries()

        assertEquals(1, summaries.size)
        assertEquals("section", summaries.single().section)
        assertEquals(2_000_000L, summaries.single().averageNanos)
    }

    @Test
    fun resetClearsLatestSummaries() {
        T9ResponsivenessTrace.configure(enabled = true, aggregationWindow = 1)
        T9ResponsivenessTrace.record("section", 1_000_000L)

        assertTrue(T9ResponsivenessTrace.latestSummaries().isNotEmpty())

        T9ResponsivenessTrace.reset()

        assertTrue(T9ResponsivenessTrace.latestSummaries().isEmpty())
    }

    @Test
    fun inputTransactionReportsEndToEndStagesAndPercentiles() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            nanoTime = { now }
        )

        val inputId = T9ResponsivenessTrace.beginInput(
            path = "CHINESE/PINYIN",
            requiresSourceEvent = true
        )
        now = 1_000_000L
        T9ResponsivenessTrace.markDecisionComplete(inputId)
        now = 4_000_000L
        T9ResponsivenessTrace.markSourceEvent()
        now = 7_000_000L
        T9ResponsivenessTrace.markSnapshotReady(inputId)
        now = 9_000_000L
        T9ResponsivenessTrace.markRenderComplete(inputId)
        now = 12_000_000L

        val summary = T9ResponsivenessTrace.completeFrame(inputId)!!

        assertEquals("CHINESE/PINYIN", summary.path)
        assertEquals(1, summary.count)
        assertEquals(12_000_000L, summary.averageNanos)
        assertEquals(12_000_000L, summary.p50Nanos)
        assertEquals(12_000_000L, summary.p95Nanos)
        assertEquals(1_000_000L, summary.averageDecisionNanos)
        assertEquals(0L, summary.averageEffectNanos)
        assertEquals(3_000_000L, summary.averageSourceWaitNanos)
        assertEquals(0L, summary.averageEngineQueueNanos)
        assertEquals(0L, summary.averageEngineDispatchNanos)
        assertEquals(3_000_000L, summary.averageSourceCallbackNanos)
        assertEquals(3_000_000L, summary.averageSnapshotNanos)
        assertEquals(2_000_000L, summary.averageRenderNanos)
        assertEquals(3_000_000L, summary.averageFrameWaitNanos)
    }

    @Test
    fun engineSourceWaitSeparatesQueueDispatchAndCallbackLatency() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            nanoTime = { now }
        )

        val inputId = T9ResponsivenessTrace.beginInput(
            path = "CHINESE/PINYIN",
            requiresSourceEvent = true
        )
        now = 1L
        T9ResponsivenessTrace.markDecisionComplete(inputId)
        now = 2L
        T9ResponsivenessTrace.markEffectApplied(inputId)
        now = 5L
        T9ResponsivenessTrace.markEngineDispatchStarted(inputId)
        now = 9L
        T9ResponsivenessTrace.markEngineDispatchCompleted(inputId)
        now = 12L
        T9ResponsivenessTrace.markSourceEvent(inputId)
        now = 14L
        T9ResponsivenessTrace.markSnapshotReady(inputId)
        now = 16L
        T9ResponsivenessTrace.markRenderComplete(inputId)
        now = 20L

        val summary = T9ResponsivenessTrace.completeFrame(inputId)!!

        assertEquals(10L, summary.averageSourceWaitNanos)
        assertEquals(3L, summary.averageEngineQueueNanos)
        assertEquals(4L, summary.averageEngineDispatchNanos)
        assertEquals(3L, summary.averageSourceCallbackNanos)
    }

    @Test
    fun staleSourceReceiptCannotCompleteTheNewerInput() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            nanoTime = { now }
        )

        val staleId = T9ResponsivenessTrace.beginInput(
            path = "CHINESE/PINYIN",
            requiresSourceEvent = true
        )
        now = 1L
        val currentId = T9ResponsivenessTrace.beginInput(
            path = "CHINESE/PINYIN",
            requiresSourceEvent = true
        )
        now = 2L
        T9ResponsivenessTrace.markSourceEvent(staleId)
        now = 3L
        T9ResponsivenessTrace.markSnapshotReady(currentId)
        T9ResponsivenessTrace.markRenderComplete(currentId)

        assertNull(T9ResponsivenessTrace.completeFrame(currentId))

        now = 4L
        T9ResponsivenessTrace.markSourceEvent(currentId)
        now = 5L
        T9ResponsivenessTrace.markSnapshotReady(currentId)
        T9ResponsivenessTrace.markRenderComplete(currentId)
        now = 6L

        assertEquals(5L, T9ResponsivenessTrace.completeFrame(currentId)!!.averageNanos)
    }

    @Test
    fun newerInputRejectsStaleFrameAndCountsReplacement() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            nanoTime = { now }
        )

        val staleId = T9ResponsivenessTrace.beginInput("SMART_ENGLISH")
        now = 1_000_000L
        val currentId = T9ResponsivenessTrace.beginInput("SMART_ENGLISH")
        now = 2_000_000L
        T9ResponsivenessTrace.markDecisionComplete(currentId)
        now = 3_000_000L
        T9ResponsivenessTrace.markSnapshotReady(currentId)
        now = 4_000_000L
        T9ResponsivenessTrace.markRenderComplete(currentId)
        now = 5_000_000L

        assertNull(T9ResponsivenessTrace.completeFrame(staleId))
        val summary = T9ResponsivenessTrace.completeFrame(currentId)!!

        assertEquals(1, summary.replacedCount)
        assertEquals(4_000_000L, summary.averageNanos)
        assertEquals(summary, T9ResponsivenessTrace.latestInputSummaries().single())
    }

    @Test
    fun engineInputRejectsUnrelatedUiPassBeforeSourceEvent() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            nanoTime = { now }
        )

        val inputId = T9ResponsivenessTrace.beginInput(
            path = "CHINESE/ZHUYIN",
            requiresSourceEvent = true
        )
        now = 1L
        T9ResponsivenessTrace.markSnapshotReady(inputId)
        now = 2L
        T9ResponsivenessTrace.markRenderComplete(inputId)
        now = 3L
        assertNull(T9ResponsivenessTrace.completeFrame(inputId))

        now = 4L
        T9ResponsivenessTrace.markSourceEvent()
        now = 5L
        T9ResponsivenessTrace.markSnapshotReady(inputId)
        now = 6L
        T9ResponsivenessTrace.markRenderComplete(inputId)
        now = 7L

        val summary = T9ResponsivenessTrace.completeFrame(inputId)!!
        assertEquals(7L, summary.averageNanos)
        assertEquals(1L, summary.averageSnapshotNanos)
        assertEquals(1L, summary.averageRenderNanos)
        assertEquals(1L, summary.averageFrameWaitNanos)
    }

    @Test
    fun synchronousEngineEventProducesNonOverlappingStageDurations() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            nanoTime = { now }
        )

        val inputId = T9ResponsivenessTrace.beginInput(
            path = "CHINESE/PINYIN",
            requiresSourceEvent = true
        )
        now = 2L
        T9ResponsivenessTrace.markSourceEvent()
        now = 4L
        T9ResponsivenessTrace.markDecisionComplete(inputId)
        now = 5L
        T9ResponsivenessTrace.markSnapshotReady(inputId)
        now = 6L
        T9ResponsivenessTrace.markRenderComplete(inputId)
        now = 7L

        val summary = T9ResponsivenessTrace.completeFrame(inputId)!!
        val stageTotal = summary.averageDecisionNanos +
            summary.averageEffectNanos +
            summary.averageSourceWaitNanos +
            summary.averageSnapshotNanos +
            summary.averageRenderNanos +
            summary.averageFrameWaitNanos

        assertEquals(summary.averageNanos, stageTotal)
        assertEquals(2L, summary.averageDecisionNanos)
        assertEquals(0L, summary.averageSourceWaitNanos)
    }

    @Test
    fun editorEffectCompletesWithoutCandidateFrame() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            nanoTime = { now }
        )

        val inputId = T9ResponsivenessTrace.beginInput(
            path = "ENGLISH/TEXT_INPUT",
            completionKind = T9ResponsivenessTrace.CompletionKind.EFFECT
        )
        now = 1L
        T9ResponsivenessTrace.markDecisionComplete(inputId)
        now = 4L
        T9ResponsivenessTrace.markEffectApplied(inputId)

        val summary = T9ResponsivenessTrace.completeEffect(inputId)!!

        assertEquals(4L, summary.averageNanos)
        assertEquals(1L, summary.averageDecisionNanos)
        assertEquals(3L, summary.averageEffectNanos)
        assertEquals(0L, summary.averageFrameWaitNanos)
    }

    @Test
    fun inputSurfaceTransactionWaitsForItsFrameEndpoint() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            nanoTime = { now }
        )

        val inputId = T9ResponsivenessTrace.beginInput(
            path = "ENGLISH/CASE",
            completionKind = T9ResponsivenessTrace.CompletionKind.INPUT_SURFACE_FRAME
        )
        now = 1L
        T9ResponsivenessTrace.markDecisionComplete(inputId)
        now = 3L
        T9ResponsivenessTrace.markEffectApplied(inputId)
        now = 8L

        assertNull(T9ResponsivenessTrace.completeEffect(inputId))
        val summary = T9ResponsivenessTrace.completeInputSurfaceFrame(inputId)!!

        assertEquals(2L, summary.averageEffectNanos)
        assertEquals(5L, summary.averageFrameWaitNanos)
    }

    @Test
    fun localInputWithoutSourceEventUsesDecisionAsSnapshotBaseline() {
        var now = 10L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            nanoTime = { now }
        )

        val inputId = T9ResponsivenessTrace.beginInput("SMART_ENGLISH")
        now = 20L
        T9ResponsivenessTrace.markDecisionComplete(inputId)
        now = 50L
        T9ResponsivenessTrace.markSnapshotReady(inputId)
        now = 60L
        T9ResponsivenessTrace.markRenderComplete(inputId)
        now = 80L

        val summary = T9ResponsivenessTrace.completeFrame(inputId)!!

        assertEquals(0L, summary.averageSourceWaitNanos)
        assertEquals(30L, summary.averageSnapshotNanos)
        assertEquals(10L, summary.averageRenderNanos)
        assertEquals(20L, summary.averageFrameWaitNanos)
    }

    @Test
    fun localInputIgnoresUnrelatedEngineSourceEvent() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            nanoTime = { now }
        )

        val inputId = T9ResponsivenessTrace.beginInput("SMART_ENGLISH")
        now = 1L
        T9ResponsivenessTrace.markDecisionComplete(inputId)
        now = 2L
        T9ResponsivenessTrace.markSourceEvent()
        now = 3L
        T9ResponsivenessTrace.markSnapshotReady(inputId)
        now = 4L
        T9ResponsivenessTrace.markRenderComplete(inputId)
        now = 5L

        val summary = T9ResponsivenessTrace.completeFrame(inputId)!!

        assertEquals(0L, summary.averageSourceWaitNanos)
        assertEquals(2L, summary.averageSnapshotNanos)
    }

    @Test
    fun inputSummaryUsesNearestRankPercentiles() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 4,
            nanoTime = { now }
        )
        var summary: T9ResponsivenessTrace.InputLatencySummary? = null

        listOf(1L, 2L, 3L, 100L).forEach { duration ->
            val inputId = T9ResponsivenessTrace.beginInput("SMART_ENGLISH")
            T9ResponsivenessTrace.markDecisionComplete(inputId)
            T9ResponsivenessTrace.markSnapshotReady(inputId)
            T9ResponsivenessTrace.markRenderComplete(inputId)
            now += duration
            summary = T9ResponsivenessTrace.completeFrame(inputId) ?: summary
        }

        val completed = requireNotNull(summary)
        assertEquals(2L, completed.p50Nanos)
        assertEquals(100L, completed.p95Nanos)
        assertEquals(100L, completed.maxNanos)
    }

    @Test
    fun developerReportExposesPartialInputWindowWithoutFlushingLogs() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 20,
            nanoTime = { now }
        )

        val inputId = T9ResponsivenessTrace.beginInput("SMART_ENGLISH")
        T9ResponsivenessTrace.markDecisionComplete(inputId)
        T9ResponsivenessTrace.markSnapshotReady(inputId)
        T9ResponsivenessTrace.markRenderComplete(inputId)
        now = 5_000_000L

        assertNull(T9ResponsivenessTrace.completeFrame(inputId))
        val summary = T9ResponsivenessTrace.latestInputSummaries().single()

        assertEquals("SMART_ENGLISH", summary.path)
        assertEquals(1, summary.count)
        assertEquals(5_000_000L, summary.p95Nanos)
    }

    @Test
    fun disabledTraceDoesNotStartInputTransaction() {
        T9ResponsivenessTrace.configure(enabled = false)

        assertNull(T9ResponsivenessTrace.beginInput("NUMBER"))
        assertTrue(T9ResponsivenessTrace.latestInputSummaries().isEmpty())
    }

    @Test
    fun nestedSectionTimingIsExplicitlyOptIn() {
        var now = 0L
        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            detailedSections = false,
            nanoTime = { now++ }
        )

        T9ResponsivenessTrace.measure("section") { Unit }

        assertTrue(T9ResponsivenessTrace.latestSummaries().isEmpty())

        T9ResponsivenessTrace.configure(
            enabled = true,
            aggregationWindow = 1,
            detailedSections = true,
            nanoTime = { now++ }
        )
        T9ResponsivenessTrace.measure("section") { Unit }

        assertEquals("section", T9ResponsivenessTrace.latestSummaries().single().section)
    }
}

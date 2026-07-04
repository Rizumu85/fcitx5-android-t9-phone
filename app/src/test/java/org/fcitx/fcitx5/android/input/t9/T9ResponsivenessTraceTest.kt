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
}

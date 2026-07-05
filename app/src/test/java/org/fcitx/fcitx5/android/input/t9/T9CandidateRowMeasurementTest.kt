/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateRowMeasurementTest {
    @Test
    fun contentChangeInvalidatesPreviousWidth() {
        val measurement = T9CandidateRowMeasurement()

        assertTrue(measurement.markContent("a"))
        measurement.remember(120)
        assertEquals(120, measurement.currentWidthPx())

        assertTrue(measurement.markContent("b"))
        assertNull(measurement.currentWidthPx())
    }

    @Test
    fun sameContentKeepsMeasuredWidth() {
        val measurement = T9CandidateRowMeasurement()

        measurement.markContent("a")
        measurement.remember(120)

        assertFalse(measurement.markContent("a"))
        assertEquals(120, measurement.currentWidthPx())
    }

    @Test
    fun staleSignatureMeasurementIsIgnored() {
        val measurement = T9CandidateRowMeasurement()

        measurement.markContent("current")
        measurement.remember(120, signature = "old")

        assertNull(measurement.currentWidthPx())
    }

    @Test
    fun clearDropsContentAndWidth() {
        val measurement = T9CandidateRowMeasurement()

        measurement.markContent("a")
        measurement.remember(120)
        measurement.clear()

        assertEquals("", measurement.currentSignature)
        assertNull(measurement.currentWidthPx())
    }
}

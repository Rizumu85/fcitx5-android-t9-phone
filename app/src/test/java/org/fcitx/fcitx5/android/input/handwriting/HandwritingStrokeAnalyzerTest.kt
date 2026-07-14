/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingStrokeAnalyzerTest {
    @Test
    fun horizontalStrokeMatchesRepositoryDirectionConvention() {
        val result = HandwritingStrokeAnalyzer.analyze(
            listOf(stroke(20f to 100f, 220f to 100f))
        )

        assertEquals(1, result.strokeCount)
        assertEquals(1, result.subStrokes.size)
        assertTrue(result.subStrokes.single().direction in 0..2)
    }

    @Test
    fun downwardStrokeUsesScreenCoordinateDirection() {
        val result = HandwritingStrokeAnalyzer.analyze(
            listOf(stroke(100f to 20f, 100f to 220f))
        )

        assertEquals(192, result.subStrokes.single().direction)
    }

    @Test
    fun cornerCreatesMultipleSubstrokes() {
        val result = HandwritingStrokeAnalyzer.analyze(
            listOf(stroke(20f to 20f, 220f to 20f, 220f to 220f))
        )

        assertEquals(2, result.subStrokes.size)
    }

    private fun stroke(vararg points: Pair<Float, Float>) = HandwritingStroke(
        points.mapIndexed { index, point ->
            HandwritingPoint(point.first, point.second, index * 16L)
        }
    )
}

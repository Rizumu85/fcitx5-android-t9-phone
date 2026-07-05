/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEnglishPredictionSessionTest {

    @Test
    fun contextShowsPredictionsAndAllowsSelection() {
        val session = session("good" to listOf("morning", "night"))

        session.updateContext(listOf("good"))
        assertEquals(listOf("morning", "night"), session.rawCandidates())

        assertTrue(session.moveCandidate(1))
        assertEquals("night", session.selectedCandidate())
    }

    @Test
    fun hiddenPredictionStopsExposingCandidatesUntilContextChanges() {
        val session = session(
            "good" to listOf("morning"),
            "hello" to listOf("there")
        )

        session.updateContext(listOf("good"))
        assertTrue(session.hide())

        assertFalse(session.isVisible)
        assertEquals(emptyList<String>(), session.rawCandidates())

        session.updateContext(listOf("hello"))
        assertEquals(listOf("there"), session.rawCandidates())
    }

    @Test
    fun invalidSelectionIsRejected() {
        val session = session("good" to listOf("morning"))

        session.updateContext(listOf("good"))

        assertFalse(session.setCandidateIndex(4))
        assertEquals("morning", session.selectedCandidate())
    }

    private fun session(vararg predictions: Pair<String, List<String>>): SmartEnglishPredictionSession =
        SmartEnglishPredictionSession(
            predictionProvider = { context, limit ->
                predictions.toMap()[context.last()].orEmpty().take(limit)
            },
            candidateLimit = 10
        )
}

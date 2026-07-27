/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ChinesePredictionCandidateSessionTest {
    private val source = ticket("64", revision = 2)
    private val idle = ticket("", revision = 3)

    @Test
    fun waitsForCandidateEventAfterCommittedComposition() {
        val session = ChinesePredictionCandidateSession()
        session.arm(source, enabled = true)

        assertEquals(
            ChinesePredictionCandidateSession.Phase.WAITING,
            session.evaluate(true, idle, "", page("你"))
        )

        session.onCandidateEvent()

        assertEquals(
            ChinesePredictionCandidateSession.Phase.VISIBLE,
            session.evaluate(true, idle, "", page("你", "好"))
        )
    }

    @Test
    fun staleCompositionCandidatesNeverBecomePredictions() {
        val session = ChinesePredictionCandidateSession()
        session.arm(source, enabled = true)
        session.onCandidateEvent()

        assertEquals(
            ChinesePredictionCandidateSession.Phase.WAITING,
            session.evaluate(true, source, "ni", page("你"))
        )
    }

    @Test
    fun newCompositionCancelsVisiblePredictions() {
        val session = ChinesePredictionCandidateSession()
        session.arm(source, enabled = true)
        session.onCandidateEvent()
        session.evaluate(true, idle, "", page("你"))

        assertEquals(
            ChinesePredictionCandidateSession.Phase.OFF,
            session.evaluate(true, ticket("4", revision = 4), "g", page("个"))
        )
    }

    @Test
    fun partialPhraseSelectionDoesNotStartPrediction() {
        val session = ChinesePredictionCandidateSession()
        session.arm(source, enabled = true)
        session.onCandidateEvent()

        assertEquals(
            ChinesePredictionCandidateSession.Phase.OFF,
            session.evaluate(true, ticket("6", revision = 3), "m", page("们"))
        )
    }

    @Test
    fun disabledModeCannotOwnAnIdleCandidateRow() {
        val session = ChinesePredictionCandidateSession()
        session.arm(source, enabled = true)
        session.onCandidateEvent()

        assertEquals(
            ChinesePredictionCandidateSession.Phase.OFF,
            session.evaluate(false, idle, "", page("你"))
        )
    }

    private fun ticket(raw: String, revision: Long) = ChineseT9CompositionTicket(
        scheme = ChineseT9Scheme.PINYIN,
        rawSequence = raw,
        digitSequence = raw.filter(Char::isDigit),
        sessionRevision = revision
    )

    private fun page(vararg text: String) = FcitxEvent.PagedCandidateEvent.Data(
        candidates = text.map { FcitxEvent.Candidate("", it, "") }.toTypedArray(),
        cursorIndex = 0,
        layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
        hasPrev = false,
        hasNext = false
    )
}

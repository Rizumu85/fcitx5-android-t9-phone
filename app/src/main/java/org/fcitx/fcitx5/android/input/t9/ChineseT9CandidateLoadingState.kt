/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class ChineseT9CandidateLoadingState {
    enum class State {
        IDLE,
        WAITING_FOR_ENGINE
    }

    var state: State = State.IDLE
        private set
    private var engineResultObserved = false
    private var expectedTicket: ChineseT9CompositionTicket? = null
    private var candidateEventTicket: ChineseT9CompositionTicket? = null

    fun reset() {
        state = State.IDLE
        engineResultObserved = false
        expectedTicket = null
        candidateEventTicket = null
    }

    fun startIfNeeded(
        chineseT9Active: Boolean,
        ticket: ChineseT9CompositionTicket
    ): Boolean {
        val hasCompositionCode = ticket.digitSequence.any { token ->
            token.isDigit() && ticket.scheme.acceptsCompositionDigit(token.digitToInt())
        }
        state = if (chineseT9Active && hasCompositionCode) {
            engineResultObserved = false
            expectedTicket = ticket
            candidateEventTicket = null
            State.WAITING_FOR_ENGINE
        } else {
            engineResultObserved = false
            expectedTicket = null
            candidateEventTicket = null
            State.IDLE
        }
        return state == State.WAITING_FOR_ENGINE
    }

    fun onEngineCandidates(
        data: FcitxEvent.PagedCandidateEvent.Data,
        ticket: ChineseT9CompositionTicket,
        enginePreedit: String
    ): Boolean {
        if (ticket.digitSequence.isEmpty()) {
            reset()
            return true
        }
        if (ticket != expectedTicket) return false
        candidateEventTicket = ticket
        return releaseIfFresh(data, ticket, enginePreedit)
    }

    fun onEngineInputPanel(
        data: FcitxEvent.PagedCandidateEvent.Data,
        ticket: ChineseT9CompositionTicket,
        enginePreedit: String
    ): Boolean {
        if (ticket.digitSequence.isEmpty()) {
            reset()
            return true
        }
        if (ticket != expectedTicket || candidateEventTicket != ticket) return false
        return releaseIfFresh(data, ticket, enginePreedit)
    }

    private fun releaseIfFresh(
        data: FcitxEvent.PagedCandidateEvent.Data,
        ticket: ChineseT9CompositionTicket,
        enginePreedit: String
    ): Boolean {
        val accepted = ChineseT9CandidateFreshness.matches(
                data = data,
                scheme = ticket.scheme,
                digitSequence = ticket.digitSequence,
                enginePreedit = enginePreedit
            )
        if (accepted) {
            state = State.IDLE
            engineResultObserved = true
        }
        return accepted
    }

    fun shouldWaitForCandidates(
        chineseT9Active: Boolean,
        compositionKeyCount: Int,
        hasPendingPunctuation: Boolean,
        pendingPinyinSelection: Boolean,
        rawCandidatesEmpty: Boolean
    ): Boolean =
        chineseT9Active &&
            compositionKeyCount > 0 &&
            !hasPendingPunctuation &&
            !pendingPinyinSelection &&
            (state == State.WAITING_FOR_ENGINE ||
                (rawCandidatesEmpty && !engineResultObserved))
}

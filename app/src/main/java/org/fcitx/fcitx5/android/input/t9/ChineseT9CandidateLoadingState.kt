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
    private var expectedReceipt: ChineseT9InputReceipt? = null
    private var candidateEventTicket: ChineseT9CompositionTicket? = null
    private var inputPanelEventTicket: ChineseT9CompositionTicket? = null
    private var requireSourcePair = false

    fun reset() {
        state = State.IDLE
        engineResultObserved = false
        expectedReceipt = null
        candidateEventTicket = null
        inputPanelEventTicket = null
        requireSourcePair = false
    }

    fun startIfNeeded(
        chineseT9Active: Boolean,
        receipt: ChineseT9InputReceipt,
        requireSourcePair: Boolean = false
    ): Boolean {
        val ticket = receipt.compositionTicket
        val hasCompositionCode = ticket.digitSequence.any { token ->
            token.isDigit() && ticket.scheme.acceptsCompositionDigit(token.digitToInt())
        }
        state = if (chineseT9Active && hasCompositionCode) {
            engineResultObserved = false
            expectedReceipt = receipt
            candidateEventTicket = null
            inputPanelEventTicket = null
            this.requireSourcePair = requireSourcePair
            State.WAITING_FOR_ENGINE
        } else {
            engineResultObserved = false
            expectedReceipt = null
            candidateEventTicket = null
            inputPanelEventTicket = null
            this.requireSourcePair = false
            State.IDLE
        }
        return state == State.WAITING_FOR_ENGINE
    }

    fun onEngineCandidates(
        data: FcitxEvent.PagedCandidateEvent.Data,
        ticket: ChineseT9CompositionTicket,
        enginePreedit: String
    ): ChineseT9InputReceipt? {
        if (ticket.digitSequence.isEmpty()) {
            val accepted = expectedReceipt
            reset()
            return accepted
        }
        val receipt = expectedReceipt ?: return null
        if (ticket != receipt.compositionTicket) return null
        candidateEventTicket = ticket
        if (requireSourcePair && inputPanelEventTicket != ticket) return null
        return releaseIfFresh(data, receipt, enginePreedit)
    }

    fun onEngineInputPanel(
        data: FcitxEvent.PagedCandidateEvent.Data,
        ticket: ChineseT9CompositionTicket,
        enginePreedit: String
    ): ChineseT9InputReceipt? {
        if (ticket.digitSequence.isEmpty()) {
            val accepted = expectedReceipt
            reset()
            return accepted
        }
        val receipt = expectedReceipt ?: return null
        if (ticket != receipt.compositionTicket) return null
        if (requireSourcePair) {
            if (
                ChineseT9CandidateFreshness.matchesEnginePreedit(
                    scheme = ticket.scheme,
                    digitSequence = ticket.digitSequence,
                    enginePreedit = enginePreedit
                )
            ) {
                // Fcitx flushes InputPanel before PagedCandidate for one engine frame. Waiting
                // for the candidate event after this matching preedit prevents a replay prefix's
                // page from being paired with the final preedit during partial phrase commits.
                inputPanelEventTicket = ticket
                candidateEventTicket = null
            }
            return null
        }
        inputPanelEventTicket = ticket
        if (candidateEventTicket != ticket) return null
        return releaseIfFresh(data, receipt, enginePreedit)
    }

    fun restoreCachedFrame(
        data: FcitxEvent.PagedCandidateEvent.Data,
        receipt: ChineseT9InputReceipt,
        enginePreedit: String
    ): ChineseT9InputReceipt? {
        val ticket = receipt.compositionTicket
        if (ticket.digitSequence.isEmpty()) {
            reset()
            return receipt
        }
        expectedReceipt = receipt
        candidateEventTicket = ticket
        inputPanelEventTicket = ticket
        state = State.WAITING_FOR_ENGINE
        engineResultObserved = false
        return releaseIfFresh(data, receipt, enginePreedit)
    }

    private fun releaseIfFresh(
        data: FcitxEvent.PagedCandidateEvent.Data,
        receipt: ChineseT9InputReceipt,
        enginePreedit: String
    ): ChineseT9InputReceipt? {
        val ticket = receipt.compositionTicket
        val accepted = ChineseT9CandidateFreshness.matches(
                data = data,
                scheme = ticket.scheme,
                digitSequence = ticket.digitSequence,
                enginePreedit = enginePreedit
            )
        if (accepted) {
            state = State.IDLE
            engineResultObserved = true
            expectedReceipt = null
            candidateEventTicket = null
            inputPanelEventTicket = null
            requireSourcePair = false
        }
        return receipt.takeIf { accepted }
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

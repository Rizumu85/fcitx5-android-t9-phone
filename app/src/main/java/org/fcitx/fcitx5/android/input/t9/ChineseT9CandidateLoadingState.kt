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

    fun reset() {
        state = State.IDLE
        engineResultObserved = false
        expectedReceipt = null
        candidateEventTicket = null
    }

    fun startIfNeeded(
        chineseT9Active: Boolean,
        receipt: ChineseT9InputReceipt
    ): Boolean {
        val ticket = receipt.compositionTicket
        val hasCompositionCode = ticket.digitSequence.any { token ->
            token.isDigit() && ticket.scheme.acceptsCompositionDigit(token.digitToInt())
        }
        state = if (chineseT9Active && hasCompositionCode) {
            engineResultObserved = false
            expectedReceipt = receipt
            candidateEventTicket = null
            State.WAITING_FOR_ENGINE
        } else {
            engineResultObserved = false
            expectedReceipt = null
            candidateEventTicket = null
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
        if (ticket != receipt.compositionTicket || candidateEventTicket != ticket) return null
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

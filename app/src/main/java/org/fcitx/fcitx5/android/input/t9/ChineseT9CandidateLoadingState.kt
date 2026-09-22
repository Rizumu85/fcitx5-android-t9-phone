/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class ChineseT9CandidateLoadingState {
    enum class State { IDLE, WAITING_FOR_ENGINE }

    var state: State = State.IDLE
        private set
    private var engineResultObserved = false
    private var expectedReceipt: ChineseT9InputReceipt? = null

    fun reset() {
        state = State.IDLE
        engineResultObserved = false
        expectedReceipt = null
    }

    fun startIfNeeded(chineseT9Active: Boolean, receipt: ChineseT9InputReceipt): Boolean {
        reset()
        val ticket = receipt.compositionTicket
        if (chineseT9Active && ticket.digitSequence.any {
                it.isDigit() && ticket.scheme.acceptsCompositionDigit(it.digitToInt())
            }
        ) {
            state = State.WAITING_FOR_ENGINE
            expectedReceipt = receipt
        }
        return state == State.WAITING_FOR_ENGINE
    }

    fun onEngineFrame(
        data: FcitxEvent.PagedCandidateEvent.Data,
        receipt: ChineseT9InputReceipt,
        enginePreedit: String
    ): ChineseT9InputReceipt? {
        val expected = expectedReceipt
        if (expected != null && expected.compositionTicket != receipt.compositionTicket) return null
        if (!ChineseT9CandidateFreshness.matches(
                data, receipt.compositionTicket.scheme, receipt.compositionTicket.digitSequence, enginePreedit
            )
        ) return null
        // Only complete, origin-validated native frames reach this Interface. In particular,
        // a page from one flush cannot be paired with the preedit from the following flush.
        state = State.IDLE
        engineResultObserved = true
        expectedReceipt = null
        return expected
    }

    fun shouldWaitForCandidates(
        chineseT9Active: Boolean,
        compositionKeyCount: Int,
        hasPendingPunctuation: Boolean,
        rawCandidatesEmpty: Boolean
    ): Boolean = chineseT9Active && compositionKeyCount > 0 && !hasPendingPunctuation &&
        (state == State.WAITING_FOR_ENGINE || (rawCandidatesEmpty && !engineResultObserved))
}

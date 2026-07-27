/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class ChinesePredictionCandidateSession {
    enum class Phase {
        OFF,
        WAITING,
        VISIBLE
    }

    private var candidateRevision = 0L
    private var armedCandidateRevision = Long.MIN_VALUE
    private var sourceTicket: ChineseT9CompositionTicket? = null
    private var phase = Phase.OFF

    val ownsIdleSurface: Boolean
        get() = phase != Phase.OFF

    fun arm(source: ChineseT9CompositionTicket, enabled: Boolean) {
        if (!enabled) {
            reset()
            return
        }
        sourceTicket = source
        armedCandidateRevision = candidateRevision
        phase = Phase.WAITING
    }

    fun onCandidateEvent() {
        candidateRevision++
    }

    fun evaluate(
        enabled: Boolean,
        currentTicket: ChineseT9CompositionTicket,
        enginePreedit: CharSequence,
        candidates: FcitxEvent.PagedCandidateEvent.Data
    ): Phase {
        if (!enabled) {
            reset()
            return Phase.OFF
        }
        return when (phase) {
            Phase.OFF -> Phase.OFF
            Phase.WAITING -> evaluateWaiting(currentTicket, enginePreedit, candidates)
            Phase.VISIBLE -> evaluateVisible(currentTicket, enginePreedit, candidates)
        }
    }

    fun dismiss() {
        reset()
    }

    fun reset() {
        armedCandidateRevision = Long.MIN_VALUE
        sourceTicket = null
        phase = Phase.OFF
    }

    private fun evaluateWaiting(
        currentTicket: ChineseT9CompositionTicket,
        enginePreedit: CharSequence,
        candidates: FcitxEvent.PagedCandidateEvent.Data
    ): Phase {
        val source = sourceTicket ?: return Phase.OFF.also { reset() }
        val currentHasComposition = currentTicket.rawSequence.isNotEmpty() || enginePreedit.isNotEmpty()
        if (currentHasComposition) {
            // A changed non-empty ticket is new user input or a remaining phrase segment, not
            // the no-composition frame that librime-predict publishes after a full commit.
            if (currentTicket != source) reset()
            return phase
        }
        if (candidateRevision <= armedCandidateRevision) return Phase.WAITING
        phase = if (candidates.candidates.isEmpty()) Phase.OFF else Phase.VISIBLE
        if (phase == Phase.OFF) reset()
        return phase
    }

    private fun evaluateVisible(
        currentTicket: ChineseT9CompositionTicket,
        enginePreedit: CharSequence,
        candidates: FcitxEvent.PagedCandidateEvent.Data
    ): Phase {
        if (currentTicket.rawSequence.isNotEmpty() || enginePreedit.isNotEmpty() ||
            candidates.candidates.isEmpty()
        ) {
            reset()
        }
        return phase
    }
}

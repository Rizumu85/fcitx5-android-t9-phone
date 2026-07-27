/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class ChinesePredictionCandidateSession {
    enum class Phase {
        OFF,
        ARMED,
        WAITING,
        VISIBLE
    }

    class ArmToken internal constructor()

    private var candidateRevision = 0L
    private var armedCandidateRevision = Long.MIN_VALUE
    private var sourceTicket: ChineseT9CompositionTicket? = null
    private var activeArmToken: ArmToken? = null
    private var phase = Phase.OFF

    val ownsIdleSurface: Boolean
        get() = phase == Phase.WAITING || phase == Phase.VISIBLE

    fun arm(source: ChineseT9CompositionTicket, enabled: Boolean): ArmToken? {
        if (!enabled) {
            reset()
            return null
        }
        val token = ArmToken()
        activeArmToken = token
        sourceTicket = source
        armedCandidateRevision = candidateRevision
        phase = Phase.ARMED
        return token
    }

    fun cancel(token: ArmToken?) {
        if (token != null && token === activeArmToken) reset()
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
            Phase.ARMED -> evaluateArmed(currentTicket, enginePreedit, candidates)
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
        activeArmToken = null
        phase = Phase.OFF
    }

    private fun evaluateArmed(
        currentTicket: ChineseT9CompositionTicket,
        enginePreedit: CharSequence,
        candidates: FcitxEvent.PagedCandidateEvent.Data
    ): Phase {
        val source = sourceTicket ?: return Phase.OFF.also { reset() }
        val currentHasComposition = currentTicket.rawSequence.isNotEmpty() || enginePreedit.isNotEmpty()
        if (currentHasComposition) {
            // A partial selection still owns the ordinary Chinese surface. Prediction may take
            // ownership only after the engine has actually cleared the committed composition.
            if (currentTicket != source) reset()
            return phase
        }
        phase = Phase.WAITING
        return resolveWaitingCandidateEvent(candidates)
    }

    private fun evaluateWaiting(
        currentTicket: ChineseT9CompositionTicket,
        enginePreedit: CharSequence,
        candidates: FcitxEvent.PagedCandidateEvent.Data
    ): Phase {
        val currentHasComposition = currentTicket.rawSequence.isNotEmpty() || enginePreedit.isNotEmpty()
        if (currentHasComposition) {
            reset()
            return Phase.OFF
        }
        return resolveWaitingCandidateEvent(candidates)
    }

    private fun resolveWaitingCandidateEvent(
        candidates: FcitxEvent.PagedCandidateEvent.Data
    ): Phase {
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

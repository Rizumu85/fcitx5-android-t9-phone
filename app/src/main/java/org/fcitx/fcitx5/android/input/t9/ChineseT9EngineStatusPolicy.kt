/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object ChineseT9EngineStatusPolicy {
    fun status(
        readiness: RimeAvailabilitySession.EngineReadiness,
        inputBlocked: Boolean,
        userSchemeHandoffPending: Boolean
    ): T9CandidateStatus? =
        when (readiness) {
            RimeAvailabilitySession.EngineReadiness.READY -> null
            RimeAvailabilitySession.EngineReadiness.DEPLOYING,
            RimeAvailabilitySession.EngineReadiness.ACTIVATING_INPUT_METHOD ->
                if (inputBlocked) {
                    T9CandidateStatus.RIME_UNAVAILABLE
                } else {
                    T9CandidateStatus.RIME_PREPARING
                }
            RimeAvailabilitySession.EngineReadiness.SELECTING_SCHEMA ->
                when {
                    inputBlocked -> T9CandidateStatus.RIME_UNAVAILABLE
                    // The mode badge already acknowledges an intentional switch. Keep the
                    // asynchronous handoff quiet without hiding cold-start or recovery waits.
                    userSchemeHandoffPending -> null
                    else -> T9CandidateStatus.RIME_PREPARING
                }
            RimeAvailabilitySession.EngineReadiness.UNAVAILABLE ->
                T9CandidateStatus.RIME_UNAVAILABLE.takeIf { inputBlocked }
        }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseT9EngineStatusPolicyTest {
    @Test
    fun deliberateRuntimeSchemeHandoffDoesNotLookLikeDictionaryProvisioning() {
        assertEquals(
            null,
            ChineseT9EngineStatusPolicy.status(
                readiness = RimeAvailabilitySession.EngineReadiness.SELECTING_SCHEMA,
                inputBlocked = false,
                userSchemeHandoffPending = true
            )
        )
    }

    @Test
    fun recoverySchemaSelectionStillExplainsWhyChineseInputIsWaiting() {
        assertEquals(
            T9CandidateStatus.RIME_PREPARING,
            ChineseT9EngineStatusPolicy.status(
                readiness = RimeAvailabilitySession.EngineReadiness.SELECTING_SCHEMA,
                inputBlocked = false,
                userSchemeHandoffPending = false
            )
        )
    }

    @Test
    fun deploymentAndInputMethodActivationRemainVisible() {
        listOf(
            RimeAvailabilitySession.EngineReadiness.DEPLOYING,
            RimeAvailabilitySession.EngineReadiness.ACTIVATING_INPUT_METHOD
        ).forEach { readiness ->
            assertEquals(
                T9CandidateStatus.RIME_PREPARING,
                ChineseT9EngineStatusPolicy.status(
                    readiness = readiness,
                    inputBlocked = false,
                    userSchemeHandoffPending = true
                )
            )
        }
    }

    @Test
    fun exhaustedHandoffReportsUnavailableInsteadOfStayingSilent() {
        assertEquals(
            T9CandidateStatus.RIME_UNAVAILABLE,
            ChineseT9EngineStatusPolicy.status(
                readiness = RimeAvailabilitySession.EngineReadiness.SELECTING_SCHEMA,
                inputBlocked = true,
                userSchemeHandoffPending = true
            )
        )
    }
}

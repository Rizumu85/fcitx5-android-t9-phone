/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9SchemeCycleTest {
    @Test
    fun followsStableSchemeOrderAndWraps() {
        val enabled = listOf(
            ChineseT9Scheme.PINYIN,
            ChineseT9Scheme.STROKE,
            ChineseT9Scheme.ZHUYIN
        )

        assertEquals(
            ChineseT9Scheme.STROKE,
            ChineseT9SchemeCycle.next(ChineseT9Scheme.PINYIN, enabled)
        )
        assertEquals(
            ChineseT9Scheme.PINYIN,
            ChineseT9SchemeCycle.next(ChineseT9Scheme.ZHUYIN, enabled)
        )
    }

    @Test
    fun excludedCurrentSchemeMovesToFirstEnabledScheme() {
        assertEquals(
            ChineseT9Scheme.PINYIN,
            ChineseT9SchemeCycle.next(
                current = ChineseT9Scheme.STROKE,
                enabled = listOf(ChineseT9Scheme.PINYIN)
            )
        )
    }

    @Test
    fun oneEnabledCurrentSchemeLeavesIdlePoundForReturn() {
        assertEquals(
            null,
            ChineseT9SchemeCycle.next(
                current = ChineseT9Scheme.PINYIN,
                enabled = listOf(ChineseT9Scheme.PINYIN)
            )
        )
    }

    @Test
    fun rapidRequestsAdvanceFromThePendingTarget() {
        val session = ChineseT9SchemeCycleSession()
        val enabled = ChineseT9Scheme.entries

        assertEquals(
            ChineseT9Scheme.STROKE,
            session.requestNext(ChineseT9Scheme.PINYIN, enabled)
        )
        assertEquals(
            ChineseT9Scheme.ZHUYIN,
            session.requestNext(ChineseT9Scheme.PINYIN, enabled)
        )
        assertEquals(
            ChineseT9Scheme.PINYIN,
            session.requestNext(ChineseT9Scheme.PINYIN, enabled)
        )
    }

    @Test
    fun acknowledgedRequestDoesNotReplayConfirmationIndicator() {
        val session = ChineseT9SchemeCycleSession()
        session.requestNext(ChineseT9Scheme.PINYIN, ChineseT9Scheme.entries)

        assertEquals(
            ChineseT9SchemeCycleSession.ActivationPresentation.KEEP_REQUEST_ACKNOWLEDGEMENT,
            session.observeActive(ChineseT9Scheme.STROKE)
        )
        assertEquals(
            ChineseT9SchemeCycleSession.ActivationPresentation.SHOW_CONFIRMATION,
            session.observeActive(ChineseT9Scheme.PINYIN)
        )
    }

    @Test
    fun rapidIntermediateActivationsDoNotReplaceNewerRequestIndicator() {
        val session = ChineseT9SchemeCycleSession()
        session.requestNext(ChineseT9Scheme.PINYIN, ChineseT9Scheme.entries)
        session.requestNext(ChineseT9Scheme.PINYIN, ChineseT9Scheme.entries)

        assertEquals(
            ChineseT9SchemeCycleSession.ActivationPresentation.KEEP_REQUEST_ACKNOWLEDGEMENT,
            session.observeActive(ChineseT9Scheme.STROKE)
        )
        assertEquals(
            ChineseT9SchemeCycleSession.ActivationPresentation.KEEP_REQUEST_ACKNOWLEDGEMENT,
            session.observeActive(ChineseT9Scheme.ZHUYIN)
        )
        assertEquals(
            ChineseT9SchemeCycleSession.ActivationPresentation.SHOW_CONFIRMATION,
            session.observeActive(ChineseT9Scheme.PINYIN)
        )
    }

    @Test
    fun handoffRemainsPendingUntilTheRequestedSchemeIsObserved() {
        val session = ChineseT9SchemeCycleSession()
        session.requestNext(ChineseT9Scheme.PINYIN, ChineseT9Scheme.entries)

        assertTrue(session.hasPendingHandoff)
        session.observeActive(ChineseT9Scheme.ZHUYIN)
        assertTrue(session.hasPendingHandoff)
        session.observeActive(ChineseT9Scheme.STROKE)
        assertFalse(session.hasPendingHandoff)
    }
}

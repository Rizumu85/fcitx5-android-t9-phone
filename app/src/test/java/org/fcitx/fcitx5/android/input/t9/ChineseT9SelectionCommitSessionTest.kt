/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9SelectionCommitSessionTest {

    @Test
    fun selectedTextWaitsForItsMatchingCandidateFrameAndCommitsOnce() {
        val session = ChineseT9SelectionCommitSession()
        val expected = receipt(raw = "5485426946649367487832", revision = 2)
        session.arm(expected, "最好用的")

        assertFalse(
            session.markSourceReady(receipt(raw = "548542694664936748783", revision = 1))
        )
        assertTrue(session.markSourceReady(expected))
        assertTrue(session.markFrameRendered(expected.compositionTicket))
        assertEquals(expected, session.scheduleAfterFrameDraw())
        assertNull(
            session.consumeScheduled(receipt(raw = "548542694664936748783", revision = 3))
        )
        assertEquals("最好用的", session.consumeScheduled(expected))
        assertNull(session.consumeScheduled(expected))
    }

    @Test
    fun editorCommitCannotBeScheduledBeforeTheReplacementFrameDraws() {
        val session = ChineseT9SelectionCommitSession()
        val receipt = receipt(raw = "5485426946649367487832", revision = 2)
        session.arm(receipt, "最好用的")

        assertNull(session.scheduleAfterFrameDraw())
        assertTrue(session.markSourceReady(receipt))
        assertNull(session.scheduleAfterFrameDraw())
        assertFalse(
            session.markFrameRendered(
                receipt(raw = "548542694664936748783", revision = 3).compositionTicket
            )
        )
        assertTrue(session.markFrameRendered(receipt.compositionTicket))
        assertEquals(receipt, session.scheduleAfterFrameDraw())
        assertNull(session.scheduleAfterFrameDraw())
    }

    @Test
    fun newerCurrentFrameReleasesSelectionWhoseOwnFrameWasSuperseded() {
        val session = ChineseT9SelectionCommitSession()
        val armed = receipt(raw = "426", revision = 2)
        val typedAhead = receipt(raw = "4263", revision = 3)
        session.arm(armed, "你")

        assertTrue(session.markSourceReady(typedAhead))
        assertFalse(session.markFrameRendered(armed.compositionTicket))
        assertTrue(session.markFrameRendered(typedAhead.compositionTicket))
        assertEquals(typedAhead, session.scheduleAfterFrameDraw())
        assertEquals("你", session.consumeScheduled(typedAhead))
    }

    @Test
    fun frameFromAnotherEpochCannotReleaseSelection() {
        val session = ChineseT9SelectionCommitSession()
        session.arm(receipt(raw = "426", revision = 2), "你")

        assertFalse(session.markSourceReady(receipt(raw = "4263", revision = 3, epoch = 1)))
    }

    @Test
    fun takeUnreleasedReturnsSelectedTextOnceInAnyPhase() {
        val session = ChineseT9SelectionCommitSession()
        val receipt = receipt(raw = "548", revision = 2)
        session.arm(receipt, "九")
        assertTrue(session.markSourceReady(receipt))

        assertEquals("九", session.takeUnreleased())

        assertNull(session.takeUnreleased())
        assertFalse(session.markFrameRendered(receipt.compositionTicket))
        assertNull(session.scheduleAfterFrameDraw())
        assertNull(session.consumeScheduled(receipt))
    }

    private fun receipt(raw: String, revision: Long, epoch: Long = 0): ChineseT9InputReceipt =
        ChineseT9InputReceipt(
            compositionTicket = ChineseT9CompositionTicket(
                scheme = ChineseT9Scheme.PINYIN,
                rawSequence = raw,
                digitSequence = raw,
                sessionRevision = revision,
                sessionEpoch = epoch
            ),
            traceInputId = 42L
        )
}

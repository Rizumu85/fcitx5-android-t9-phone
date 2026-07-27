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
            session.markSourceReady(receipt(raw = "548542694664936748783", revision = 3))
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
    fun cancelDropsASelectionThatCanNoLongerOwnTheComposition() {
        val session = ChineseT9SelectionCommitSession()
        val receipt = receipt(raw = "548", revision = 2)
        session.arm(receipt, "九")

        session.cancel()

        assertFalse(session.markSourceReady(receipt))
        assertNull(session.scheduleAfterFrameDraw())
        assertNull(session.consumeScheduled(receipt))
    }

    private fun receipt(raw: String, revision: Long): ChineseT9InputReceipt =
        ChineseT9InputReceipt(
            compositionTicket = ChineseT9CompositionTicket(
                scheme = ChineseT9Scheme.PINYIN,
                rawSequence = raw,
                digitSequence = raw,
                sessionRevision = revision
            ),
            traceInputId = 42L
        )
}

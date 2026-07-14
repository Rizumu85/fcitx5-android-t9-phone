/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingStrokeRenderLedgerTest {
    @Test
    fun `completion callback before coordinator publish keeps local stroke`() {
        val ledger = HandwritingStrokeRenderLedger<String>()

        ledger.expectLocalStrokeCompletion()

        assertTrue(ledger.acceptFinished(listOf("stroke-1")).isEmpty())
        assertTrue(ledger.reconcileCoordinatorCount(1).isEmpty())
    }

    @Test
    fun `undo removes latest rendered stroke`() {
        val ledger = HandwritingStrokeRenderLedger<String>()
        repeat(2) { ledger.expectLocalStrokeCompletion() }
        ledger.acceptFinished(listOf("stroke-1", "stroke-2"))

        assertEquals(setOf("stroke-2"), ledger.reconcileCoordinatorCount(1))
    }

    @Test
    fun `late completion after clear is removed`() {
        val ledger = HandwritingStrokeRenderLedger<String>()
        ledger.expectLocalStrokeCompletion()
        ledger.reconcileCoordinatorCount(0)

        assertEquals(setOf("stroke-1"), ledger.acceptFinished(listOf("stroke-1")))
    }
}

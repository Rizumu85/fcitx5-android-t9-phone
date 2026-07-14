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
        val ledger = HandwritingStrokeRenderLedger<String, Int>()

        ledger.expectLocalStrokeCompletion("stroke-1")

        assertEquals(listOf(1), ledger.acceptFinished(mapOf("stroke-1" to 1)))
        assertEquals(listOf(1), ledger.reconcileCoordinatorCount(1))
    }

    @Test
    fun `undo removes latest rendered stroke`() {
        val ledger = HandwritingStrokeRenderLedger<String, Int>()
        ledger.expectLocalStrokeCompletion("stroke-1")
        ledger.expectLocalStrokeCompletion("stroke-2")
        ledger.acceptFinished(mapOf("stroke-1" to 1, "stroke-2" to 2))

        assertEquals(listOf(1), ledger.reconcileCoordinatorCount(1))
    }

    @Test
    fun `late completion after clear is removed`() {
        val ledger = HandwritingStrokeRenderLedger<String, Int>()
        ledger.expectLocalStrokeCompletion("stroke-1")
        ledger.reconcileCoordinatorCount(0)

        assertTrue(ledger.acceptFinished(mapOf("stroke-1" to 1)).isEmpty())
    }

    @Test
    fun `out of order completion callbacks preserve local finish order`() {
        val ledger = HandwritingStrokeRenderLedger<String, Int>()
        ledger.expectLocalStrokeCompletion("stroke-1")
        ledger.expectLocalStrokeCompletion("stroke-2")

        assertEquals(listOf(2), ledger.acceptFinished(mapOf("stroke-2" to 2)))
        assertEquals(listOf(1, 2), ledger.acceptFinished(mapOf("stroke-1" to 1)))
        assertEquals(listOf(1), ledger.reconcileCoordinatorCount(1))
    }
}

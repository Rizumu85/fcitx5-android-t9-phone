/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.*
import org.junit.Test

class ChineseT9SourceRegistryTest {
    @Test fun sameDigitsDoNotMakeDifferentReadingGenerationsInterchangeable() {
        val source = ChineseT9SourceRegistry()
        val typed = ChineseT9CompositionTicket(ChineseT9Scheme.PINYIN, "43", "43", 1, 2)
        val chosen = typed.copy(sessionRevision = 2)
        val old = source.register(ChineseT9InputReceipt(typed, null))
        val latest = source.register(ChineseT9InputReceipt(chosen, 7))
        assertNull(source.find(old, chosen))
        assertEquals(7L, source.find(latest, chosen)!!.traceInputId)
        assertNull(source.find(latest, chosen.copy(sessionEpoch = 3)))
    }

    @Test fun clearingTheRegistryCannotReuseAnOldNativeSourceId() {
        val source = ChineseT9SourceRegistry()
        val ticket = ChineseT9CompositionTicket(ChineseT9Scheme.PINYIN, "4", "4", 1)
        val receipt = ChineseT9InputReceipt(ticket, null)
        val old = source.register(receipt)
        source.clear()
        val latest = source.register(receipt)
        assertNotEquals(old, latest)
        assertNull(source.find(old, ticket))
        assertEquals(receipt, source.find(latest, ticket))
    }
}

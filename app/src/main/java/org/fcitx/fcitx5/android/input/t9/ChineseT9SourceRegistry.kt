/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.t9

import java.util.concurrent.atomic.AtomicLong

class ChineseT9SourceRegistry {
    private val receipts = LinkedHashMap<Long, ChineseT9InputReceipt>()

    fun register(receipt: ChineseT9InputReceipt): Long {
        val id = nextId.incrementAndGet()
        receipts[id] = receipt
        if (receipts.size > 256) receipts.remove(receipts.keys.first())
        return id
    }

    fun find(id: Long, current: ChineseT9CompositionTicket): ChineseT9InputReceipt? =
        receipts[id]?.takeIf { it.compositionTicket == current }

    fun clear() = receipts.clear()

    companion object {
        // Fcitx may outlive an Android input view or service instance; never reuse its ids.
        private val nextId = AtomicLong()
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class ChineseT9SelectionCommitSession {
    private enum class Phase {
        ARMED,
        SOURCE_READY,
        FRAME_RENDERED,
        COMMIT_SCHEDULED
    }

    private data class PendingCommit(
        val receipt: ChineseT9InputReceipt,
        val text: String,
        val phase: Phase
    )

    private var pending: PendingCommit? = null

    fun arm(receipt: ChineseT9InputReceipt, text: String) {
        require(text.isNotEmpty())
        pending = PendingCommit(receipt, text, Phase.ARMED)
    }

    fun markSourceReady(receipt: ChineseT9InputReceipt): Boolean {
        val value = pending?.takeIf {
            it.receipt == receipt && it.phase == Phase.ARMED
        } ?: return false
        pending = value.copy(phase = Phase.SOURCE_READY)
        return true
    }

    fun markFrameRendered(ticket: ChineseT9CompositionTicket?): Boolean {
        val value = pending?.takeIf {
            it.phase == Phase.SOURCE_READY &&
                it.receipt.compositionTicket == ticket
        } ?: return false
        pending = value.copy(phase = Phase.FRAME_RENDERED)
        return true
    }

    fun scheduleAfterFrameDraw(): ChineseT9InputReceipt? {
        val value = pending?.takeIf { it.phase == Phase.FRAME_RENDERED } ?: return null
        pending = value.copy(phase = Phase.COMMIT_SCHEDULED)
        return value.receipt
    }

    fun consumeScheduled(receipt: ChineseT9InputReceipt): String? {
        val value = pending?.takeIf {
            it.receipt == receipt && it.phase == Phase.COMMIT_SCHEDULED
        } ?: return null
        pending = null
        return value.text
    }

    fun cancel() {
        pending = null
    }
}

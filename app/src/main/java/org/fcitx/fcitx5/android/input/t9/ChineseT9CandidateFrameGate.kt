/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object ChineseT9CandidateFrameGate {
    data class Input(
        val chineseSurface: Boolean,
        val engineCandidatesPending: Boolean,
        val bulkCandidatesPending: Boolean,
        val hasBulkCandidatePage: Boolean
    )

    fun shouldDefer(input: Input): Boolean {
        if (!input.chineseSurface) return false
        if (input.engineCandidatesPending) return true
        // Product decision: Chinese T9 candidate rows should switch atomically. Showing Rime's
        // short current page before the bulk-budgeted page arrives creates a visible half-row
        // flash, such as `gel HDL` one frame before the full `gel HDL Hardware ...` row.
        return input.bulkCandidatesPending && !input.hasBulkCandidatePage
    }
}

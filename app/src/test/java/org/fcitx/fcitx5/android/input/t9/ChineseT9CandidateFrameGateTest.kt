/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9CandidateFrameGateTest {
    @Test
    fun defersChineseFrameWhenBulkCandidatesArePendingWithoutPage() {
        assertTrue(
            ChineseT9CandidateFrameGate.shouldDefer(
                ChineseT9CandidateFrameGate.Input(
                    chineseSurface = true,
                    engineCandidatesPending = false,
                    bulkCandidatesPending = true,
                    hasBulkCandidatePage = false
                )
            )
        )
    }

    @Test
    fun allowsFrameWhenBulkPageIsAvailableOrSurfaceIsNotChinese() {
        assertFalse(
            ChineseT9CandidateFrameGate.shouldDefer(
                ChineseT9CandidateFrameGate.Input(
                    chineseSurface = true,
                    engineCandidatesPending = false,
                    bulkCandidatesPending = true,
                    hasBulkCandidatePage = true
                )
            )
        )
        assertFalse(
            ChineseT9CandidateFrameGate.shouldDefer(
                ChineseT9CandidateFrameGate.Input(
                    chineseSurface = false,
                    engineCandidatesPending = false,
                    bulkCandidatesPending = true,
                    hasBulkCandidatePage = false
                )
            )
        )
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9CandidateFreshnessTest {
    @Test
    fun staleShorterPinyinCommentDoesNotMatchLongerDigitSequence() {
        assertFalse(
            ChineseT9CandidateFreshness.matchesDigitSequence(
                data = paged(candidate("个", comment = "ge")),
                digitSequence = "435"
            )
        )
    }

    @Test
    fun latinCandidateTextCanMatchCurrentDigitSequence() {
        assertTrue(
            ChineseT9CandidateFreshness.matchesDigitSequence(
                data = paged(candidate("gel")),
                digitSequence = "435"
            )
        )
        assertTrue(
            ChineseT9CandidateFreshness.matchesDigitSequence(
                data = paged(candidate("HelloFresh")),
                digitSequence = "435"
            )
        )
    }

    @Test
    fun fullPinyinCommentCanMatchCurrentDigitSequence() {
        assertTrue(
            ChineseT9CandidateFreshness.matchesDigitSequence(
                data = paged(candidate("你好", comment = "ni hao")),
                digitSequence = "64426"
            )
        )
    }

    private fun paged(vararg candidates: FcitxEvent.Candidate): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = candidates.toList().toTypedArray(),
            cursorIndex = 0,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )

    private fun candidate(text: String, comment: String = ""): FcitxEvent.Candidate =
        FcitxEvent.Candidate(label = "", text = text, comment = comment)
}

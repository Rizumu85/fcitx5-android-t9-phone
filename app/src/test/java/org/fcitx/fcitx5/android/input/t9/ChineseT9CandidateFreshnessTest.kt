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
            ChineseT9CandidateFreshness.matches(
                data = paged(candidate("个", comment = "ge")),
                scheme = ChineseT9Scheme.PINYIN,
                digitSequence = "435",
                enginePreedit = "ge"
            )
        )
    }

    @Test
    fun latinCandidateTextCanMatchCurrentDigitSequence() {
        assertTrue(
            ChineseT9CandidateFreshness.matches(
                data = paged(candidate("gel")),
                scheme = ChineseT9Scheme.PINYIN,
                digitSequence = "435",
                enginePreedit = "gel"
            )
        )
        assertTrue(
            ChineseT9CandidateFreshness.matches(
                data = paged(candidate("HelloFresh")),
                scheme = ChineseT9Scheme.PINYIN,
                digitSequence = "435",
                enginePreedit = "hello"
            )
        )
    }

    @Test
    fun fullPinyinCommentCanMatchCurrentDigitSequence() {
        assertTrue(
            ChineseT9CandidateFreshness.matches(
                data = paged(candidate("你好", comment = "ni hao")),
                scheme = ChineseT9Scheme.PINYIN,
                digitSequence = "64426",
                enginePreedit = "nihao"
            )
        )
    }

    @Test
    fun strokeUsesEnginePreeditBecauseCompletionCommentsOnlyContainSuffixes() {
        assertFalse(
            ChineseT9CandidateFreshness.matches(
                data = paged(candidate("一", comment = "~h")),
                scheme = ChineseT9Scheme.STROKE,
                digitSequence = "12",
                enginePreedit = "一"
            )
        )
        assertTrue(
            ChineseT9CandidateFreshness.matches(
                data = paged(candidate("下", comment = "~h")),
                scheme = ChineseT9Scheme.STROKE,
                digitSequence = "12",
                enginePreedit = "一丨"
            )
        )
    }

    @Test
    fun strokeWildcardMatchesOnlyItsRequestedPositions() {
        assertTrue(
            ChineseT9CandidateFreshness.matches(
                data = paged(candidate("不")),
                scheme = ChineseT9Scheme.STROKE,
                digitSequence = "16",
                enginePreedit = "一一"
            )
        )
        assertFalse(
            ChineseT9CandidateFreshness.matches(
                data = paged(candidate("旧")),
                scheme = ChineseT9Scheme.STROKE,
                digitSequence = "16",
                enginePreedit = "丨一"
            )
        )
    }

    @Test
    fun zhuyinCandidateCommentMatchesPhoneKeyGroups() {
        assertTrue(
            ChineseT9CandidateFreshness.matches(
                data = paged(candidate("好", comment = "ㄏㄠ")),
                scheme = ChineseT9Scheme.ZHUYIN,
                digitSequence = "38",
                enginePreedit = "38"
            )
        )
        assertFalse(
            ChineseT9CandidateFreshness.matches(
                data = paged(candidate("个", comment = "ㄍㄜ")),
                scheme = ChineseT9Scheme.ZHUYIN,
                digitSequence = "38",
                enginePreedit = "38"
            )
        )
    }

    @Test
    fun rawSchemesCanFinishWithAnEmptyCandidatePageButPinyinStillWaits() {
        assertTrue(
            ChineseT9CandidateFreshness.matches(
                data = paged(),
                scheme = ChineseT9Scheme.STROKE,
                digitSequence = "12",
                enginePreedit = "一丨"
            )
        )
        assertTrue(
            ChineseT9CandidateFreshness.matches(
                data = paged(),
                scheme = ChineseT9Scheme.ZHUYIN,
                digitSequence = "38",
                enginePreedit = "38"
            )
        )
        assertFalse(
            ChineseT9CandidateFreshness.matches(
                data = paged(),
                scheme = ChineseT9Scheme.PINYIN,
                digitSequence = "64",
                enginePreedit = "ni"
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

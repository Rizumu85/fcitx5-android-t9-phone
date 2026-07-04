/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9CandidateLoadingStateTest {

    @Test
    fun waitsAfterChineseCompositionStartsUntilEngineCandidatesArrive() {
        val state = ChineseT9CandidateLoadingState()

        state.startIfNeeded(chineseT9Active = true, compositionKeyCount = 1)

        assertTrue(state.shouldWaitForCandidates(
            chineseT9Active = true,
            compositionKeyCount = 1,
            hasPendingPunctuation = false,
            pendingPinyinSelection = false,
            rawCandidatesEmpty = false
        ))

        state.onEngineCandidates(paged("你"), compositionKeyCount = 1)

        assertFalse(state.shouldWaitForCandidates(
            chineseT9Active = true,
            compositionKeyCount = 1,
            hasPendingPunctuation = false,
            pendingPinyinSelection = false,
            rawCandidatesEmpty = false
        ))
    }

    @Test
    fun emptyRawCandidatesStillWaitForChineseComposingState() {
        val state = ChineseT9CandidateLoadingState()

        assertTrue(state.shouldWaitForCandidates(
            chineseT9Active = true,
            compositionKeyCount = 1,
            hasPendingPunctuation = false,
            pendingPinyinSelection = false,
            rawCandidatesEmpty = true
        ))
    }

    @Test
    fun punctuationAndPinyinSelectionSuppressWaiting() {
        val state = ChineseT9CandidateLoadingState()
        state.startIfNeeded(chineseT9Active = true, compositionKeyCount = 1)

        assertFalse(state.shouldWaitForCandidates(
            chineseT9Active = true,
            compositionKeyCount = 1,
            hasPendingPunctuation = true,
            pendingPinyinSelection = false,
            rawCandidatesEmpty = true
        ))
        assertFalse(state.shouldWaitForCandidates(
            chineseT9Active = true,
            compositionKeyCount = 1,
            hasPendingPunctuation = false,
            pendingPinyinSelection = true,
            rawCandidatesEmpty = true
        ))
    }

    private fun paged(text: String): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = arrayOf(FcitxEvent.Candidate(label = "", text = text, comment = "")),
            cursorIndex = 0,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
}

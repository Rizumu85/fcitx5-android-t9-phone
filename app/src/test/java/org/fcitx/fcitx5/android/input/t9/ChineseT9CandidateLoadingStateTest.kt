/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.*
import org.junit.Test

class ChineseT9CandidateLoadingStateTest {
    @Test fun completeMatchingFrameReleasesExactlyItsReceipt() {
        val state = ChineseT9CandidateLoadingState()
        val input = receipt("64", 1)
        assertTrue(state.startIfNeeded(true, input))
        assertTrue(waiting(state))
        assertEquals(input, state.onEngineFrame(page("ni"), input, "ni"))
        assertFalse(waiting(state))
        assertNull(state.onEngineFrame(page("ni"), input, "ni"))
    }

    @Test fun sameDigitsFromAnOlderReadingChoiceCannotReleaseTheNewChoice() {
        val state = ChineseT9CandidateLoadingState()
        val old = receipt("43", 1)
        val selected = receipt("43", 2)
        state.startIfNeeded(true, selected)
        assertNull(state.onEngineFrame(page("he"), old, "he"))
        assertTrue(waiting(state))
        assertEquals(selected, state.onEngineFrame(page("ge"), selected, "ge"))
    }

    @Test fun retypedCompositionHasADifferentEpoch() {
        val state = ChineseT9CandidateLoadingState()
        val old = receipt("64", 1)
        val new = old.copy(compositionTicket = old.compositionTicket.copy(sessionEpoch = 2))
        state.startIfNeeded(true, new)
        assertNull(state.onEngineFrame(page("ni"), old, "ni"))
        assertEquals(new, state.onEngineFrame(page("ni"), new, "ni"))
    }

    @Test fun replayPrefixCannotReleaseTheFinalComposition() {
        val state = ChineseT9CandidateLoadingState()
        val input = receipt("64426", 1)
        state.startIfNeeded(true, input)
        assertNull(state.onEngineFrame(page("ni"), input, "ni"))
        assertTrue(waiting(state))
        assertEquals(input, state.onEngineFrame(page("ni hao"), input, "ni hao"))
    }

    @Test fun emptyResultWithMatchingPreeditIsACompleteNoMatchFrame() {
        val state = ChineseT9CandidateLoadingState()
        val input = receipt("99", 1)
        state.startIfNeeded(true, input)
        assertEquals(input, state.onEngineFrame(FcitxEvent.PagedCandidateEvent.Data.Empty, input, "99"))
        assertFalse(waiting(state))
    }

    @Test fun restoringACompleteCacheDoesNotWaitForAnotherEngineEvent() {
        val state = ChineseT9CandidateLoadingState()
        val input = receipt("64", 1)
        state.onEngineFrame(page("ni"), input, "ni")
        assertFalse(waiting(state))
    }

    @Test fun replacingExpectedInputKeepsTheNewTraceReceipt() {
        val state = ChineseT9CandidateLoadingState()
        val first = receipt("6", 1)
        val latest = receipt("64", 2)
        state.startIfNeeded(true, first)
        state.startIfNeeded(true, latest)
        assertNull(state.onEngineFrame(page("o"), first, "o"))
        assertEquals(latest, state.onEngineFrame(page("ni"), latest, "ni"))
    }

    @Test fun strokeAndZhuyinRetainTheirSchemeSpecificCompletenessChecks() {
        for ((scheme, raw, preview) in listOf(
            Triple(ChineseT9Scheme.STROKE, "16", "一丨"),
            Triple(ChineseT9Scheme.ZHUYIN, "38", "38")
        )) {
            val state = ChineseT9CandidateLoadingState()
            val input = receipt(raw, 1, scheme)
            assertTrue(state.startIfNeeded(true, input))
            assertEquals(input, state.onEngineFrame(FcitxEvent.PagedCandidateEvent.Data.Empty, input, preview))
            assertFalse(waiting(state))
        }
    }

    @Test fun idleNonChineseAndPunctuationNeverWaitForChineseCandidates() {
        val state = ChineseT9CandidateLoadingState()
        assertFalse(state.startIfNeeded(false, receipt("64", 1)))
        assertFalse(state.startIfNeeded(true, receipt("", 2)))
        state.startIfNeeded(true, receipt("64", 3))
        assertFalse(state.shouldWaitForCandidates(true, 2, true, true))
        assertFalse(state.shouldWaitForCandidates(false, 2, false, true))
        assertFalse(state.shouldWaitForCandidates(true, 0, false, true))
        state.reset()
        assertEquals(ChineseT9CandidateLoadingState.State.IDLE, state.state)
    }

    private fun waiting(state: ChineseT9CandidateLoadingState) =
        state.shouldWaitForCandidates(true, 2, false, true)

    private fun receipt(raw: String, revision: Long, scheme: ChineseT9Scheme = ChineseT9Scheme.PINYIN) =
        ChineseT9InputReceipt(ChineseT9CompositionTicket(scheme, raw, raw, revision), revision)

    private fun page(comment: String) = FcitxEvent.PagedCandidateEvent.Data(
        arrayOf(FcitxEvent.Candidate("", "test", comment)), 0,
        FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal, false, false
    )
}

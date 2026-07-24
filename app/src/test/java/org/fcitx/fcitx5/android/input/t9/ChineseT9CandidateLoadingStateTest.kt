/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9CandidateLoadingStateTest {

    @Test
    fun cachedFreshFrameRestoresLateAttachedCandidateSurface() {
        val state = ChineseT9CandidateLoadingState()
        val ticket = ticket(ChineseT9Scheme.PINYIN, "94664")

        assertNotNull(
            state.restoreCachedFrame(
                data = paged("中", "zhong"),
                receipt = receipt(ticket),
                enginePreedit = "zhong"
            )
        )
        assertFalse(
            state.shouldWaitForCandidates(
                chineseT9Active = true,
                compositionKeyCount = 5,
                hasPendingPunctuation = false,
                pendingPinyinSelection = false,
                rawCandidatesEmpty = false
            )
        )
    }

    @Test
    fun longPinyinFrameReleasesFromCurrentEnginePreeditDespiteAmbiguousComment() {
        val state = ChineseT9CandidateLoadingState()
        val ticket = ticket(ChineseT9Scheme.PINYIN, "946649366674494233")
        state.startIfNeeded(chineseT9Active = true, receipt = receipt(ticket))

        assertNotNull(
            state.onEngineCandidates(
                data = paged("中叶弄湿下的", "zhong ye nong shi xia de"),
                ticket = ticket,
                enginePreedit = "94664 93 666 744 942 33"
            )
        )
        assertFalse(waiting(state, compositionKeyCount = 18))
    }

    @Test
    fun waitsAfterChineseCompositionStartsUntilEngineCandidatesArrive() {
        val state = ChineseT9CandidateLoadingState()

        state.startIfNeeded(
            chineseT9Active = true,
            receipt = receipt(ticket(ChineseT9Scheme.PINYIN, "64"))
        )

        assertTrue(state.shouldWaitForCandidates(
            chineseT9Active = true,
            compositionKeyCount = 1,
            hasPendingPunctuation = false,
            pendingPinyinSelection = false,
            rawCandidatesEmpty = false
        ))

        val sourceReady = state.onEngineCandidates(
            data = paged("你", comment = "ni"),
            ticket = ticket(ChineseT9Scheme.PINYIN, "64"),
            enginePreedit = "ni"
        )

        assertNotNull(sourceReady)
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
        state.startIfNeeded(
            chineseT9Active = true,
            receipt = receipt(ticket(ChineseT9Scheme.PINYIN, "64"))
        )

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

    @Test
    fun staleCandidatePageDoesNotReleaseWaitingState() {
        val state = ChineseT9CandidateLoadingState()

        state.startIfNeeded(
            chineseT9Active = true,
            receipt = receipt(ticket(ChineseT9Scheme.PINYIN, "435"))
        )
        val staleSourceReady = state.onEngineCandidates(
            data = paged("个", comment = "ge"),
            ticket = ticket(ChineseT9Scheme.PINYIN, "435"),
            enginePreedit = "ge"
        )

        assertNull(staleSourceReady)
        assertTrue(state.shouldWaitForCandidates(
            chineseT9Active = true,
            compositionKeyCount = 3,
            hasPendingPunctuation = false,
            pendingPinyinSelection = false,
            rawCandidatesEmpty = false
        ))

        state.onEngineCandidates(
            data = paged("gel"),
            ticket = ticket(ChineseT9Scheme.PINYIN, "435"),
            enginePreedit = "gel"
        )

        assertFalse(state.shouldWaitForCandidates(
            chineseT9Active = true,
            compositionKeyCount = 3,
            hasPendingPunctuation = false,
            pendingPinyinSelection = false,
            rawCandidatesEmpty = false
        ))
    }

    @Test
    fun strokeWaitsForMatchingEnginePreeditAndSupportsUnknownStrokeResults() {
        val state = ChineseT9CandidateLoadingState()

        state.startIfNeeded(
            chineseT9Active = true,
            receipt = receipt(ticket(ChineseT9Scheme.STROKE, "12"))
        )
        state.onEngineCandidates(
            data = paged("一"),
            ticket = ticket(ChineseT9Scheme.STROKE, "12"),
            enginePreedit = "一"
        )
        assertTrue(waiting(state, compositionKeyCount = 2))

        state.onEngineCandidates(
            data = paged("下"),
            ticket = ticket(ChineseT9Scheme.STROKE, "12"),
            enginePreedit = "一丨"
        )
        assertFalse(waiting(state, compositionKeyCount = 2))

        state.startIfNeeded(
            chineseT9Active = true,
            receipt = receipt(ticket(ChineseT9Scheme.STROKE, "16", revision = 2))
        )
        state.onEngineCandidates(
            data = paged("不"),
            ticket = ticket(ChineseT9Scheme.STROKE, "16", revision = 2),
            enginePreedit = "一一"
        )
        assertFalse(waiting(state, compositionKeyCount = 2))
    }

    @Test
    fun zhuyinRejectsStaleReadingBeforeReleasingCandidateFrame() {
        val state = ChineseT9CandidateLoadingState()

        state.startIfNeeded(
            chineseT9Active = true,
            receipt = receipt(ticket(ChineseT9Scheme.ZHUYIN, "38"))
        )
        state.onEngineCandidates(
            data = paged("个", comment = "ㄍㄜ"),
            ticket = ticket(ChineseT9Scheme.ZHUYIN, "38"),
            enginePreedit = "38"
        )
        assertTrue(waiting(state, compositionKeyCount = 2))

        state.onEngineCandidates(
            data = paged("好", comment = "ㄏㄠ"),
            ticket = ticket(ChineseT9Scheme.ZHUYIN, "38"),
            enginePreedit = "38"
        )
        assertFalse(waiting(state, compositionKeyCount = 2))
    }

    @Test
    fun matchingRawCodePreeditReleasesAnIntentionallyEmptyResult() {
        val state = ChineseT9CandidateLoadingState()

        state.startIfNeeded(
            chineseT9Active = true,
            receipt = receipt(ticket(ChineseT9Scheme.STROKE, "12"))
        )
        state.onEngineCandidates(
            data = emptyPaged(),
            ticket = ticket(ChineseT9Scheme.STROKE, "12"),
            enginePreedit = "一丨"
        )

        assertFalse(
            state.shouldWaitForCandidates(
                chineseT9Active = true,
                compositionKeyCount = 2,
                hasPendingPunctuation = false,
                pendingPinyinSelection = false,
                rawCandidatesEmpty = true
            )
        )
    }

    @Test
    fun staleCompositionTicketCannotReleaseNewGeneration() {
        val state = ChineseT9CandidateLoadingState()
        val old = ticket(ChineseT9Scheme.STROKE, "1", revision = 1)
        val current = ticket(ChineseT9Scheme.STROKE, "12", revision = 2)
        state.startIfNeeded(chineseT9Active = true, receipt = receipt(old))
        state.startIfNeeded(chineseT9Active = true, receipt = receipt(current))

        state.onEngineCandidates(
            data = paged("一"),
            ticket = old,
            enginePreedit = "一"
        )

        assertTrue(waiting(state, compositionKeyCount = 2))
    }

    @Test
    fun laterInputPanelCanCompleteCurrentCandidateEventPair() {
        val state = ChineseT9CandidateLoadingState()
        val ticket = ticket(ChineseT9Scheme.STROKE, "12")
        val candidates = paged("下")
        state.startIfNeeded(chineseT9Active = true, receipt = receipt(ticket))
        state.onEngineCandidates(
            data = candidates,
            ticket = ticket,
            enginePreedit = "一"
        )
        assertTrue(waiting(state, compositionKeyCount = 2))

        val sourceReady = state.onEngineInputPanel(
            data = candidates,
            ticket = ticket,
            enginePreedit = "一丨"
        )

        assertNotNull(sourceReady)
        assertFalse(waiting(state, compositionKeyCount = 2))
    }

    @Test
    fun inputPanelAloneCannotReuseCandidatesFromBeforeTicket() {
        val state = ChineseT9CandidateLoadingState()
        val ticket = ticket(ChineseT9Scheme.STROKE, "12")
        state.startIfNeeded(chineseT9Active = true, receipt = receipt(ticket))

        state.onEngineInputPanel(
            data = paged("下"),
            ticket = ticket,
            enginePreedit = "一丨"
        )

        assertTrue(waiting(state, compositionKeyCount = 2))
    }

    @Test
    fun acceptedFrameReturnsTheReceiptThatStartedItsGeneration() {
        val state = ChineseT9CandidateLoadingState()
        val ticket = ticket(ChineseT9Scheme.PINYIN, "64")
        val receipt = receipt(ticket, traceInputId = 42L)
        state.startIfNeeded(chineseT9Active = true, receipt = receipt)

        val accepted = state.onEngineCandidates(
            data = paged("你", comment = "ni"),
            ticket = ticket,
            enginePreedit = "ni"
        )

        assertSame(receipt, accepted)
    }

    @Test
    fun acceptedGenerationCannotPublishASecondSourceFrame() {
        val state = ChineseT9CandidateLoadingState()
        val ticket = ticket(ChineseT9Scheme.PINYIN, "64")
        state.startIfNeeded(
            chineseT9Active = true,
            receipt = receipt(ticket, traceInputId = 42L)
        )
        state.onEngineCandidates(
            data = paged("你", comment = "ni"),
            ticket = ticket,
            enginePreedit = "ni"
        )

        val duplicate = state.onEngineInputPanel(
            data = paged("你", comment = "ni"),
            ticket = ticket,
            enginePreedit = "ni"
        )

        assertNull(duplicate)
    }

    @Test
    fun staleFrameCannotClaimTheCurrentReceipt() {
        val state = ChineseT9CandidateLoadingState()
        val staleTicket = ticket(ChineseT9Scheme.PINYIN, "4", revision = 1)
        val currentTicket = ticket(ChineseT9Scheme.PINYIN, "43", revision = 2)
        state.startIfNeeded(
            chineseT9Active = true,
            receipt = receipt(currentTicket, traceInputId = 9L)
        )

        val accepted = state.onEngineCandidates(
            data = paged("个", comment = "ge"),
            ticket = staleTicket,
            enginePreedit = "ge"
        )

        assertNull(accepted)
        assertTrue(waiting(state, compositionKeyCount = 2))
    }

    private fun waiting(
        state: ChineseT9CandidateLoadingState,
        compositionKeyCount: Int
    ): Boolean = state.shouldWaitForCandidates(
        chineseT9Active = true,
        compositionKeyCount = compositionKeyCount,
        hasPendingPunctuation = false,
        pendingPinyinSelection = false,
        rawCandidatesEmpty = false
    )

    private fun ticket(
        scheme: ChineseT9Scheme,
        digits: String,
        revision: Long = 1
    ): ChineseT9CompositionTicket = ChineseT9CompositionTicket(
        scheme = scheme,
        rawSequence = digits,
        digitSequence = digits,
        sessionRevision = revision
    )

    private fun receipt(
        ticket: ChineseT9CompositionTicket,
        traceInputId: Long? = null
    ): ChineseT9InputReceipt = ChineseT9InputReceipt(ticket, traceInputId)

    private fun paged(text: String, comment: String = ""): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = arrayOf(FcitxEvent.Candidate(label = "", text = text, comment = comment)),
            cursorIndex = 0,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )

    private fun emptyPaged(): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = emptyArray(),
            cursorIndex = -1,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
}

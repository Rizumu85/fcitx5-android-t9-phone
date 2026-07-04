/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class T9CandidateUiStateBuilderTest {

    @Test
    fun chineseSurfaceDoesNotQuerySmartEnglishState() {
        val delegate = FakeDelegate(
            chineseActive = true,
            smartEnglishActive = false
        )
        val result = T9CandidateUiStateBuilder(delegate).build(input())

        assertNotNull(result)
        assertEquals(1, delegate.syncCompositionCount)
        assertEquals(0, delegate.isSmartEnglishActiveCount)
        assertEquals(0, delegate.getSmartEnglishPagedCount)
        assertEquals(0, delegate.getSmartEnglishPresentationCount)
        assertEquals(1, delegate.getResolvedPrefixesCount)
        assertEquals(1, delegate.getT9PresentationCount)
    }

    @Test
    fun smartEnglishSurfaceDoesNotRunChineseCompositionOrFilteringWork() {
        val delegate = FakeDelegate(
            chineseActive = false,
            smartEnglishActive = true,
            smartEnglishPaged = paged("hello")
        )
        val result = T9CandidateUiStateBuilder(delegate).build(input())

        assertNotNull(result)
        assertEquals(0, delegate.syncCompositionCount)
        assertEquals(1, delegate.isSmartEnglishActiveCount)
        assertEquals(1, delegate.getSmartEnglishPagedCount)
        assertEquals(1, delegate.getSmartEnglishPresentationCount)
        assertEquals(0, delegate.getResolvedPrefixesCount)
        assertEquals(0, delegate.hasPendingPinyinSelectionCount)
        assertEquals(0, delegate.getCompositionKeyCountCount)
        assertEquals(0, delegate.filterPagedByPrefixesCount)
        assertEquals(0, delegate.requestBulkFilteredCount)
        assertEquals(0, delegate.buildCursorContextSignatureCount)
        assertEquals(0, delegate.applyHanziCursorCount)
        assertEquals(0, delegate.getT9PresentationCount)
    }

    private class FakeDelegate(
        private val chineseActive: Boolean,
        private val smartEnglishActive: Boolean,
        private val smartEnglishPaged: FcitxEvent.PagedCandidateEvent.Data? = null
    ) : T9CandidateUiStateBuilder.Delegate {
        var syncCompositionCount = 0
        var isSmartEnglishActiveCount = 0
        var getSmartEnglishPagedCount = 0
        var getSmartEnglishPresentationCount = 0
        var getResolvedPrefixesCount = 0
        var hasPendingPinyinSelectionCount = 0
        var getCompositionKeyCountCount = 0
        var filterPagedByPrefixesCount = 0
        var requestBulkFilteredCount = 0
        var buildCursorContextSignatureCount = 0
        var applyHanziCursorCount = 0
        var getT9PresentationCount = 0

        override fun syncT9CompositionWithInputPanel(inputPanel: FcitxEvent.InputPanelEvent.Data) {
            syncCompositionCount += 1
        }

        override fun isChineseT9InputModeActive(): Boolean = chineseActive

        override fun isSmartEnglishT9InputModeActive(): Boolean {
            isSmartEnglishActiveCount += 1
            return smartEnglishActive
        }

        override fun getSmartEnglishT9Paged(): FcitxEvent.PagedCandidateEvent.Data? {
            getSmartEnglishPagedCount += 1
            return smartEnglishPaged
        }

        override fun buildSmartEnglishPaged(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates = T9PagedCandidates.passthrough(data)

        override fun getT9ResolvedPinyinFilterPrefixes(): List<String> {
            getResolvedPrefixesCount += 1
            return emptyList()
        }

        override fun getPendingT9PunctuationPaged(): FcitxEvent.PagedCandidateEvent.Data? = null

        override fun buildT9PendingPunctuationPaged(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates = T9PagedCandidates.passthrough(data)

        override fun hasPendingT9PinyinSelection(): Boolean {
            hasPendingPinyinSelectionCount += 1
            return false
        }

        override fun getT9CompositionKeyCount(): Int {
            getCompositionKeyCountCount += 1
            return 1
        }

        override fun resetT9BulkFilterState() = Unit

        override fun requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active: Boolean, prefixes: List<String>) {
            requestBulkFilteredCount += 1
        }

        override fun filterPagedByT9PinyinPrefixes(
            data: FcitxEvent.PagedCandidateEvent.Data,
            prefixes: List<String>
        ): Pair<T9PagedCandidates, String?> {
            filterPagedByPrefixesCount += 1
            return T9PagedCandidates.passthrough(data) to null
        }

        override fun buildLocalBudgetedPagedFromCurrentPage(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates? = null

        override fun resetT9LocalBudgetState() = Unit

        override fun buildT9CursorContextSignature(prefixes: List<String>): String {
            buildCursorContextSignatureCount += 1
            return prefixes.joinToString()
        }

        override fun applyT9HanziCursor(
            data: FcitxEvent.PagedCandidateEvent.Data,
            cursorContextSignature: String
        ): FcitxEvent.PagedCandidateEvent.Data {
            applyHanziCursorCount += 1
            return data
        }

        override fun getSmartEnglishT9Presentation(): T9PresentationState? {
            getSmartEnglishPresentationCount += 1
            return null
        }

        override fun getT9PresentationState(
            inputPanel: FcitxEvent.InputPanelEvent.Data,
            effectivePaged: FcitxEvent.PagedCandidateEvent.Data
        ): T9PresentationState {
            getT9PresentationCount += 1
            return T9PresentationState(topReading = null, pinyinOptions = emptyList())
        }

        override fun clearHiddenChineseT9CompositionIfCandidateUiSuppressed() = Unit

        override fun effectiveT9CandidateFocus(
            pinyinOptions: List<String>,
            useT9PinyinRow: Boolean
        ): T9CandidateFocus = T9CandidateFocus.BOTTOM
    }

    private fun input(): T9CandidateUiStateBuilder.Input =
        T9CandidateUiStateBuilder.Input(
            t9InputModeEnabled = true,
            inputPanel = FcitxEvent.InputPanelEvent.Data(),
            rawPaged = paged("你"),
            orientation = FloatingCandidatesOrientation.Horizontal,
            currentlyVisible = false,
            loadingState = ChineseT9CandidateLoadingState(),
            bulkFilteredPaged = null,
            bulkFilteredMatchedPrefix = null,
            bulkFilterPending = false
        )

    private fun paged(text: String): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = arrayOf(FcitxEvent.Candidate(label = "", text = text, comment = "")),
            cursorIndex = 0,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
}

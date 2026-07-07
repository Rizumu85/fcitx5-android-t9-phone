/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
        assertEquals(1, delegate.chineseSnapshotCount)
        assertEquals(0, delegate.isSmartEnglishActiveCount)
        assertEquals(0, delegate.getSmartEnglishPagedCount)
        assertEquals(0, delegate.getSmartEnglishPresentationCount)
        assertEquals(1, delegate.getT9PresentationCount)
        assertFalse(result!!.renderState.preferAboveCursorAnchor)
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
        assertEquals(0, delegate.chineseSnapshotCount)
        assertEquals(1, delegate.isSmartEnglishActiveCount)
        assertEquals(1, delegate.getSmartEnglishPagedCount)
        assertEquals(1, delegate.getSmartEnglishPresentationCount)
        assertEquals(0, delegate.filterPagedByPrefixesCount)
        assertEquals(0, delegate.requestBulkFilteredCount)
        assertEquals(0, delegate.buildCursorContextSignatureCount)
        assertEquals(0, delegate.applyHanziCursorCount)
        assertEquals(0, delegate.getT9PresentationCount)
    }

    @Test
    fun smartEnglishPredictionCanReserveTopReadingRowWithoutText() {
        val delegate = FakeDelegate(
            chineseActive = false,
            smartEnglishActive = true,
            smartEnglishPaged = paged("morning"),
            smartEnglishPresentation = T9PresentationState(
                topReading = null,
                pinyinOptions = emptyList(),
                reserveTopReadingRow = true
            )
        )

        val result = T9CandidateUiStateBuilder(delegate).build(input())

        assertNotNull(result)
        assertTrue(result!!.renderState.reservePreeditRow)
        assertTrue(result.renderState.panel.preedit.isEmpty())
    }

    @Test
    fun smartEnglishPunctuationShowsSelectedPreviewInTopBubble() {
        val delegate = FakeDelegate(
            chineseActive = false,
            smartEnglishActive = true,
            smartEnglishPresentation = T9PresentationState(
                topReading = text("hello"),
                pinyinOptions = emptyList()
            ),
            pendingPunctuationPaged = pagedWithCursor(0, ".", ",")
        )

        val result = T9CandidateUiStateBuilder(delegate).build(input())

        assertNotNull(result)
        assertEquals(".", result!!.renderState.panel.preedit.toString())
        assertEquals(0, delegate.getSmartEnglishPresentationCount)
    }

    @Test
    fun smartEnglishPunctuationPreviewFollowsCandidateCursor() {
        val delegate = FakeDelegate(
            chineseActive = false,
            smartEnglishActive = true,
            pendingPunctuationPaged = pagedWithCursor(1, ".", ",")
        )

        val result = T9CandidateUiStateBuilder(delegate).build(input())

        assertNotNull(result)
        assertEquals(",", result!!.renderState.panel.preedit.toString())
    }

    @Test
    fun visibleChineseLoadingStateDefersRenderUntilEngineCandidatesArrive() {
        val loadingState = ChineseT9CandidateLoadingState().apply {
            startIfNeeded(chineseT9Active = true, compositionKeyCount = 1)
        }
        val delegate = FakeDelegate(
            chineseActive = true,
            smartEnglishActive = false,
            chinesePresentation = T9PresentationState(
                topReading = null,
                pinyinOptions = listOf("a", "b", "c")
            )
        )

        val result = T9CandidateUiStateBuilder(delegate).build(
            input(
                rawPaged = FcitxEvent.PagedCandidateEvent.Data.Empty,
                currentlyVisible = true,
                loadingState = loadingState
            )
        )

        assertEquals(null, result)
        assertEquals(0, delegate.getT9PresentationCount)
    }

    @Test
    fun firstChineseLoadingStateDefersLocalPinyinRowBeforeWindowIsVisible() {
        val loadingState = ChineseT9CandidateLoadingState().apply {
            startIfNeeded(chineseT9Active = true, compositionKeyCount = 1)
        }
        val delegate = FakeDelegate(
            chineseActive = true,
            smartEnglishActive = false,
            chinesePresentation = T9PresentationState(
                topReading = null,
                pinyinOptions = listOf("a", "b", "c")
            )
        )

        val result = T9CandidateUiStateBuilder(delegate).build(
            input(
                rawPaged = FcitxEvent.PagedCandidateEvent.Data.Empty,
                currentlyVisible = false,
                loadingState = loadingState
            )
        )

        assertEquals(null, result)
        assertEquals(0, delegate.getT9PresentationCount)
    }

    @Test
    fun emptyChineseCompositionSuppressesStaleCandidatesAndPresentation() {
        val delegate = FakeDelegate(
            chineseActive = true,
            smartEnglishActive = false,
            chineseSnapshot = ChineseT9InputSnapshot(
                rawSequence = "",
                digitSequence = "",
                currentSegment = "",
                fullComposition = "",
                model = T9CompositionModel(),
                keyCount = 0,
                filterPrefixes = emptyList(),
                hasPendingPinyinSelection = false,
                sessionRevision = 2
            ),
            chinesePresentation = T9PresentationState(
                topReading = text("stale"),
                pinyinOptions = listOf("stale")
            )
        )

        val result = T9CandidateUiStateBuilder(delegate).build(
            input(rawPaged = paged("旧"))
        )

        assertNotNull(result)
        assertFalse(result!!.renderState.shouldShow)
        assertTrue(result.renderState.candidates.candidates.isEmpty())
        assertTrue(result.renderState.pinyinOptions.isEmpty())
        assertEquals(0, delegate.getT9PresentationCount)
    }

    @Test
    fun repeatedChineseSnapshotReusesPresentationState() {
        val delegate = FakeDelegate(
            chineseActive = true,
            smartEnglishActive = false
        )
        val builder = T9CandidateUiStateBuilder(delegate)

        val first = builder.build(input(rawPaged = paged(candidate("你", comment = "ni"))))
        val second = builder.build(input(rawPaged = paged(candidate("你", comment = "ni"))))

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(1, delegate.getT9PresentationCount)
        assertEquals(1, delegate.presentationKeys.distinct().size)
    }

    @Test
    fun chinesePresentationRebuildsWhenCandidatePreviewChanges() {
        val delegate = FakeDelegate(
            chineseActive = true,
            smartEnglishActive = false
        )
        val builder = T9CandidateUiStateBuilder(delegate)

        builder.build(input(rawPaged = paged(candidate("你", comment = "ni"))))
        builder.build(input(rawPaged = paged(candidate("好", comment = "hao"))))

        assertEquals(2, delegate.getT9PresentationCount)
        assertEquals(listOf("ni", "hao"), delegate.presentationKeys.map { it.candidateComment })
    }

    private class FakeDelegate(
        private val chineseActive: Boolean,
        private val smartEnglishActive: Boolean,
        private val smartEnglishPaged: FcitxEvent.PagedCandidateEvent.Data? = null,
        private val smartEnglishPresentation: T9PresentationState? = null,
        private val pendingPunctuationPaged: FcitxEvent.PagedCandidateEvent.Data? = null,
        private val chineseSnapshot: ChineseT9InputSnapshot = ChineseT9InputSnapshot(
            rawSequence = "2",
            digitSequence = "2",
            currentSegment = "2",
            fullComposition = "2",
            model = T9CompositionModel(unresolvedDigits = "2", rawPreedit = "2"),
            keyCount = 1,
            filterPrefixes = emptyList(),
            hasPendingPinyinSelection = false,
            sessionRevision = 1
        ),
        private val chinesePresentation: T9PresentationState =
            T9PresentationState(topReading = null, pinyinOptions = emptyList())
    ) : T9CandidateUiStateBuilder.Delegate {
        var chineseSnapshotCount = 0
        var isSmartEnglishActiveCount = 0
        var getSmartEnglishPagedCount = 0
        var getSmartEnglishPresentationCount = 0
        var filterPagedByPrefixesCount = 0
        var requestBulkFilteredCount = 0
        var buildCursorContextSignatureCount = 0
        var applyHanziCursorCount = 0
        var getT9PresentationCount = 0
        val presentationKeys = mutableListOf<ChineseT9PresentationSnapshotKey>()

        override fun getChineseT9InputSnapshot(
            inputPanel: FcitxEvent.InputPanelEvent.Data
        ): ChineseT9InputSnapshot {
            chineseSnapshotCount += 1
            return chineseSnapshot
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

        override fun getPendingT9PunctuationPaged(): FcitxEvent.PagedCandidateEvent.Data? =
            pendingPunctuationPaged

        override fun buildT9PendingPunctuationPaged(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates = T9PagedCandidates.passthrough(data)

        override fun resetT9BulkFilterState() = Unit

        override fun requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active: Boolean, prefixes: List<String>) {
            requestBulkFilteredCount += 1
        }

        override fun getT9BulkFilterState(): ChineseT9CandidatePipeline.BulkFilterState =
            ChineseT9CandidatePipeline.BulkFilterState(
                paged = null,
                matchedPrefix = null,
                pending = false
            )

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
            return smartEnglishPresentation
        }

        override fun getT9PresentationState(key: ChineseT9PresentationSnapshotKey): T9PresentationState {
            getT9PresentationCount += 1
            presentationKeys += key
            return chinesePresentation
        }

        override fun clearHiddenChineseT9CompositionIfCandidateUiSuppressed() = Unit

        override fun effectiveT9CandidateFocus(
            pinyinOptions: List<String>,
            useT9PinyinRow: Boolean
        ): T9CandidateFocus = T9CandidateFocus.BOTTOM
    }

    private fun input(
        rawPaged: FcitxEvent.PagedCandidateEvent.Data = paged("你"),
        currentlyVisible: Boolean = false,
        loadingState: ChineseT9CandidateLoadingState = ChineseT9CandidateLoadingState()
    ): T9CandidateUiStateBuilder.Input =
        T9CandidateUiStateBuilder.Input(
            t9InputModeEnabled = true,
            inputPanel = FcitxEvent.InputPanelEvent.Data(),
            rawPaged = rawPaged,
            orientation = FloatingCandidatesOrientation.Horizontal,
            currentlyVisible = currentlyVisible,
            loadingState = loadingState
        )

    private fun paged(text: String): FcitxEvent.PagedCandidateEvent.Data =
        paged(candidate(text))

    private fun paged(vararg candidates: FcitxEvent.Candidate): FcitxEvent.PagedCandidateEvent.Data =
        pagedWithCursor(0, *candidates)

    private fun pagedWithCursor(
        cursor: Int,
        vararg words: String
    ): FcitxEvent.PagedCandidateEvent.Data =
        pagedWithCursor(cursor, *words.map(::candidate).toTypedArray())

    private fun pagedWithCursor(
        cursor: Int,
        vararg candidates: FcitxEvent.Candidate
    ): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = candidates.toList().toTypedArray(),
            cursorIndex = cursor,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )

    private fun candidate(text: String, comment: String = ""): FcitxEvent.Candidate =
        FcitxEvent.Candidate(label = "", text = text, comment = comment)

    private fun text(value: String) =
        org.fcitx.fcitx5.android.core.FormattedText(
            strings = arrayOf(value),
            flags = intArrayOf(org.fcitx.fcitx5.android.core.TextFormatFlag.NoFlag.flag),
            cursor = -1
        )
}

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
    fun chineseSurfaceUsesChinesePresentationOnly() {
        val pipeline = FakePipeline()
        val result = T9CandidateUiStateBuilder(pipeline).build(input(
            chineseActive = true,
            smartEnglishActive = false
        ))

        assertNotNull(result)
        assertEquals(1, pipeline.getT9PresentationCount)
        assertFalse(result!!.renderState.preferAboveInputPanel)
    }

    @Test
    fun smartEnglishSurfaceDoesNotRunChineseCompositionOrFilteringWork() {
        val pipeline = FakePipeline()
        val result = T9CandidateUiStateBuilder(pipeline).build(input(
            chineseActive = false,
            smartEnglishPaged = paged("hello")
        ))

        assertNotNull(result)
        assertEquals(0, pipeline.filterPagedByPrefixesCount)
        assertEquals(0, pipeline.requestBulkFilteredCount)
        assertEquals(0, pipeline.buildCursorContextSignatureCount)
        assertEquals(0, pipeline.applyHanziCursorCount)
        assertEquals(0, pipeline.getT9PresentationCount)
    }

    @Test
    fun rawChineseSchemeDoesNotRunPinyinCandidateWork() {
        val pipeline = FakePipeline()
        val result = T9CandidateUiStateBuilder(pipeline).build(input(
            rawPaged = paged("一"),
            chineseActive = true,
            chineseSnapshot = defaultChineseSnapshot().copy(
                rawSequence = "1",
                digitSequence = "1",
                currentSegment = "1",
                fullComposition = "1",
                model = T9CompositionModel(unresolvedDigits = "1", rawPreedit = "1"),
                scheme = ChineseT9Scheme.STROKE
            )
        ))

        assertNotNull(result)
        assertEquals(0, pipeline.filterPagedByPrefixesCount)
        assertEquals(0, pipeline.requestBulkFilteredCount)
        assertEquals(1, pipeline.getT9PresentationCount)
    }

    @Test
    fun smartEnglishPredictionCanReserveTopReadingRowWithoutText() {
        val result = T9CandidateUiStateBuilder(FakePipeline()).build(input(
            chineseActive = false,
            smartEnglishPaged = paged("morning"),
            smartEnglishPresentation = T9PresentationState(
                topReading = null,
                readingOptions = emptyList(),
                reserveTopReadingRow = true
            )
        ))

        assertNotNull(result)
        assertTrue(result!!.renderState.reservePreeditRow)
        assertTrue(result.renderState.panel.preedit.isEmpty())
    }

    @Test
    fun smartEnglishPunctuationShowsSelectedPreviewInTopBubble() {
        val pipeline = FakePipeline()
        val result = T9CandidateUiStateBuilder(pipeline).build(input(
            chineseActive = false,
            smartEnglishPresentation = T9PresentationState(
                topReading = text("hello"),
                readingOptions = emptyList()
            ),
            pendingPunctuationPaged = pagedWithCursor(0, ".", ",")
        ))

        assertNotNull(result)
        assertEquals(".", result!!.renderState.panel.preedit.toString())
    }

    @Test
    fun smartEnglishPunctuationPreviewFollowsCandidateCursor() {
        val result = T9CandidateUiStateBuilder(FakePipeline()).build(input(
            chineseActive = false,
            pendingPunctuationPaged = pagedWithCursor(1, ".", ",")
        ))

        assertNotNull(result)
        assertEquals(",", result!!.renderState.panel.preedit.toString())
    }

    @Test
    fun visibleChineseLoadingStateDefersRenderUntilEngineCandidatesArrive() {
        val loadingState = ChineseT9CandidateLoadingState().apply {
            startIfNeeded(
                chineseT9Active = true,
                ticket = defaultChineseSnapshot().compositionTicket()
            )
        }
        val pipeline = FakePipeline(
            chinesePresentation = T9PresentationState(
                topReading = null,
                readingOptions = listOf("a", "b", "c")
            )
        )

        val result = T9CandidateUiStateBuilder(pipeline).build(
            input(
                rawPaged = FcitxEvent.PagedCandidateEvent.Data.Empty,
                chineseActive = true,
                currentlyVisible = true,
                loadingState = loadingState
            )
        )

        assertEquals(null, result)
        assertEquals(0, pipeline.getT9PresentationCount)
    }

    @Test
    fun firstChineseLoadingStateDefersLocalPinyinRowBeforeWindowIsVisible() {
        val loadingState = ChineseT9CandidateLoadingState().apply {
            startIfNeeded(
                chineseT9Active = true,
                ticket = defaultChineseSnapshot().compositionTicket()
            )
        }
        val pipeline = FakePipeline(
            chinesePresentation = T9PresentationState(
                topReading = null,
                readingOptions = listOf("a", "b", "c")
            )
        )

        val result = T9CandidateUiStateBuilder(pipeline).build(
            input(
                rawPaged = FcitxEvent.PagedCandidateEvent.Data.Empty,
                chineseActive = true,
                currentlyVisible = false,
                loadingState = loadingState
            )
        )

        assertEquals(null, result)
        assertEquals(0, pipeline.getT9PresentationCount)
    }

    @Test
    fun emptyChineseCompositionSuppressesStaleCandidatesAndPresentation() {
        val pipeline = FakePipeline(
            chinesePresentation = T9PresentationState(
                topReading = text("stale"),
                readingOptions = listOf("stale")
            )
        )

        val result = T9CandidateUiStateBuilder(pipeline).build(input(
            chineseActive = true,
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
            )
        ).copy(rawPaged = paged("旧")))

        assertNotNull(result)
        assertFalse(result!!.renderState.shouldShow)
        assertTrue(result.renderState.candidates.candidates.isEmpty())
        assertTrue(result.renderState.readingOptions.isEmpty())
        assertEquals(0, pipeline.getT9PresentationCount)
    }

    @Test
    fun repeatedChineseSnapshotReusesPresentationState() {
        val pipeline = FakePipeline()
        val builder = T9CandidateUiStateBuilder(pipeline)

        val first = builder.build(input(
            rawPaged = paged(candidate("你", comment = "ni")),
            chineseActive = true
        ))
        val second = builder.build(input(
            rawPaged = paged(candidate("你", comment = "ni")),
            chineseActive = true
        ))

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(1, pipeline.getT9PresentationCount)
        assertEquals(1, pipeline.presentationKeys.distinct().size)
    }

    @Test
    fun chinesePresentationRebuildsWhenCandidatePreviewChanges() {
        val pipeline = FakePipeline()
        val builder = T9CandidateUiStateBuilder(pipeline)

        builder.build(input(rawPaged = paged(candidate("你", comment = "ni")), chineseActive = true))
        builder.build(input(rawPaged = paged(candidate("好", comment = "hao")), chineseActive = true))

        assertEquals(2, pipeline.getT9PresentationCount)
        assertEquals(listOf("ni", "hao"), pipeline.presentationKeys.map { it.candidateComment })
    }

    @Test
    fun pendingChineseBulkPageDefersPartialRawCandidateFrame() {
        val pipeline = FakePipeline(
            bulkFilterState = ChineseT9CandidatePipeline.BulkFilterState(
                paged = null,
                matchedPrefix = null,
                pending = true
            )
        )

        val result = T9CandidateUiStateBuilder(pipeline).build(input(
            rawPaged = paged(candidate("gel"), candidate("HDL")),
            chineseActive = true,
            chineseSnapshot = defaultChineseSnapshot().copy(
                rawSequence = "435",
                digitSequence = "435",
                currentSegment = "435",
                fullComposition = "435",
                model = T9CompositionModel(unresolvedDigits = "435", rawPreedit = "435"),
                keyCount = 3,
                filterPrefixes = listOf("gel")
            )
        ))

        assertEquals(null, result)
        assertEquals(1, pipeline.requestBulkFilteredCount)
        assertEquals(0, pipeline.getT9PresentationCount)
    }

    private class FakePipeline(
        private val chinesePresentation: T9PresentationState =
            T9PresentationState(topReading = null, readingOptions = emptyList()),
        private val bulkFilterState: ChineseT9CandidatePipeline.BulkFilterState =
            ChineseT9CandidatePipeline.BulkFilterState(
                paged = null,
                matchedPrefix = null,
                pending = false
            )
    ) : T9CandidateUiStateBuilder.Pipeline {
        var filterPagedByPrefixesCount = 0
        var requestBulkFilteredCount = 0
        var buildCursorContextSignatureCount = 0
        var applyHanziCursorCount = 0
        var getT9PresentationCount = 0
        val presentationKeys = mutableListOf<ChineseT9PresentationSnapshotKey>()

        override fun buildSmartEnglishPaged(
            snapshot: SmartEnglishUiSnapshot
        ): T9PagedCandidates? = snapshot.paged?.let(T9PagedCandidates::passthrough)

        override fun buildT9PendingPunctuationPaged(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates = T9PagedCandidates.passthrough(data)

        override fun filterT9StrokeCandidates(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates = T9PagedCandidates.passthrough(data)

        override fun resetT9BulkFilterState() = Unit

        override fun requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active: Boolean, prefixes: List<String>) {
            requestBulkFilteredCount += 1
        }

        override fun getT9BulkFilterState(): ChineseT9CandidatePipeline.BulkFilterState =
            bulkFilterState

        override fun filterPagedByT9ReadingPrefixes(
            data: FcitxEvent.PagedCandidateEvent.Data,
            prefixes: List<String>
        ): Pair<T9PagedCandidates, String?> {
            filterPagedByPrefixesCount += 1
            return T9PagedCandidates.passthrough(data) to null
        }

        override fun buildLocalBudgetedPagedFromCurrentPage(
            source: T9PagedCandidates
        ): T9PagedCandidates? = null

        override fun resetT9LocalBudgetState() = Unit

        override fun buildT9CursorContextSignature(
            preedit: CharSequence,
            prefixes: List<String>
        ): String {
            buildCursorContextSignatureCount += 1
            return "$preedit|${prefixes.joinToString()}"
        }

        override fun applyT9HanziCursor(
            data: FcitxEvent.PagedCandidateEvent.Data,
            cursorContextSignature: String
        ): FcitxEvent.PagedCandidateEvent.Data {
            applyHanziCursorCount += 1
            return data
        }

        override fun getT9PresentationState(key: ChineseT9PresentationSnapshotKey): T9PresentationState {
            getT9PresentationCount += 1
            presentationKeys += key
            return chinesePresentation
        }

        override fun clearHiddenChineseT9CompositionIfCandidateUiSuppressed() = Unit
    }

    private fun input(
        rawPaged: FcitxEvent.PagedCandidateEvent.Data = paged("你"),
        chineseActive: Boolean = true,
        smartEnglishActive: Boolean = !chineseActive,
        chineseSnapshot: ChineseT9InputSnapshot? = if (chineseActive) defaultChineseSnapshot() else null,
        smartEnglishPaged: FcitxEvent.PagedCandidateEvent.Data? = null,
        smartEnglishPresentation: T9PresentationState? = null,
        pendingPunctuationPaged: FcitxEvent.PagedCandidateEvent.Data? = null,
        currentlyVisible: Boolean = false,
        loadingState: ChineseT9CandidateLoadingState = ChineseT9CandidateLoadingState(),
        currentFocus: T9CandidateFocus = T9CandidateFocus.BOTTOM
    ): T9CandidateUiInputSnapshot =
        T9CandidateUiInputSnapshot(
            inputPanel = FcitxEvent.InputPanelEvent.Data(),
            rawPaged = rawPaged,
            orientation = FloatingCandidatesOrientation.Horizontal,
            currentlyVisible = currentlyVisible,
            loadingState = loadingState,
            widthBudget = null,
            chineseT9Active = chineseActive,
            smartEnglishActive = smartEnglishActive,
            chineseSnapshot = chineseSnapshot,
            smartEnglishSnapshot = if (
                smartEnglishPaged != null || smartEnglishPresentation != null
            ) {
                SmartEnglishUiSnapshot(
                    publicationKey = "publication",
                    contentKey = "content",
                    paged = smartEnglishPaged,
                    presentation = smartEnglishPresentation
                )
            } else {
                null
            },
            pendingPunctuationRawPaged = pendingPunctuationPaged,
            currentFocus = currentFocus
        )

    private fun defaultChineseSnapshot(): ChineseT9InputSnapshot =
        ChineseT9InputSnapshot(
            rawSequence = "2",
            digitSequence = "2",
            currentSegment = "2",
            fullComposition = "2",
            model = T9CompositionModel(unresolvedDigits = "2", rawPreedit = "2"),
            keyCount = 1,
            filterPrefixes = emptyList(),
            hasPendingPinyinSelection = false,
            sessionRevision = 1
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

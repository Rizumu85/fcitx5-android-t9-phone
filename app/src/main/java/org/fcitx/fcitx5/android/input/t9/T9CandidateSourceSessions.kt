/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9CandidateSourceSessions(
    private val characterBudget: () -> Int,
    private val widthBudget: () -> T9CandidateWidthBudget?,
    candidateMatchesPrefix: (candidate: FcitxEvent.Candidate, prefix: String) -> Boolean
) {
    private val smartEnglishPageCache = T9SmartEnglishPageCache(characterBudget, widthBudget)
    private val pendingPunctuationPager = T9CandidatePager()
    private val chineseCandidatePipeline = ChineseT9CandidatePipeline(
        characterBudget = characterBudget,
        widthBudget = widthBudget,
        candidateMatchesPrefix = candidateMatchesPrefix
    )
    // The owned bottom row needs one memory of "what the user is looking at"; scattering this
    // across each source made page/move/shortcut fixes depend on render timing in CandidatesView.
    private var currentShown: T9CandidateUiSnapshotPipeline.ShownSnapshot? = null

    val shownSource: T9CandidateUiSnapshotPipeline.ShownSource
        get() = currentShown?.source ?: T9CandidateUiSnapshotPipeline.ShownSource.OTHER

    val currentShownSnapshot: T9CandidateUiSnapshotPipeline.ShownSnapshot?
        get() = currentShown

    val hasCurrentBottomCandidateRow: Boolean
        get() = currentShown?.paged?.candidates?.isNotEmpty() == true

    val currentShownMatchedPrefix: String?
        get() = currentShown?.matchedPrefix

    fun currentChineseSelectionTicket(
        originalIndex: Int,
        selectedCandidate: FcitxEvent.Candidate
    ): T9CandidateUiSnapshotPipeline.ChineseSelectionTicket? {
        val shown = currentShown ?: return null
        when (shown.source) {
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_BULK,
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_LOCAL,
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_ENGINE -> Unit
            else -> return null
        }
        val shownIndex = shown.originalIndices.indexOfFirst { index -> index == originalIndex }
        if (shownIndex < 0) return null
        val candidate = shown.paged.candidates.getOrNull(shownIndex) ?: return null
        if (candidate != selectedCandidate) return null
        return T9CandidateUiSnapshotPipeline.ChineseSelectionTicket(
            source = shown.source,
            shownIndex = shownIndex,
            originalIndex = originalIndex,
            candidate = candidate
        )
    }

    val hasChineseLocalBudgetCandidates: Boolean
        get() = chineseCandidatePipeline.hasLocalBudgetCandidates

    val hasChineseBulkFilteredCandidates: Boolean
        get() = chineseCandidatePipeline.hasBulkFilteredCandidates

    val chineseBulkFilterState: ChineseT9CandidatePipeline.BulkFilterState
        get() = chineseCandidatePipeline.bulkFilterState

    fun reset() {
        smartEnglishPageCache.reset()
        pendingPunctuationPager.reset()
        chineseCandidatePipeline.reset()
        currentShown = null
    }

    fun invalidateShownInteraction() {
        currentShown = null
    }

    fun resetChineseLocalBudgetState() {
        chineseCandidatePipeline.resetLocalBudget()
    }

    fun resetChineseBulkFilterState() {
        chineseCandidatePipeline.resetBulkFilter()
    }

    fun chineseBulkFilterRequestSignature(
        prefixes: List<String>,
        preedit: CharSequence,
        candidates: Array<FcitxEvent.Candidate>
    ): String =
        chineseCandidatePipeline.bulkFilterRequestSignature(prefixes, preedit, candidates)

    fun shouldRequestChineseBulkFilter(signature: String): Boolean =
        chineseCandidatePipeline.shouldRequestBulkFilter(signature)

    fun startChineseBulkFilterRequest(prefixes: List<String>, signature: String) {
        chineseCandidatePipeline.startBulkFilterRequest(prefixes, signature)
    }

    fun finishChineseBulkFilterRequest(
        signature: String,
        rawCandidates: List<String>,
        prefixes: List<String>,
        layoutHint: FcitxEvent.PagedCandidateEvent.LayoutHint
    ): ChineseT9CandidatePipeline.BulkFilterState? =
        chineseCandidatePipeline.finishBulkFilterRequest(
            signature = signature,
            rawCandidates = rawCandidates,
            prefixes = prefixes,
            layoutHint = layoutHint
        )

    fun offsetChineseBulkFilteredPage(
        delta: Int,
        layoutHint: FcitxEvent.PagedCandidateEvent.LayoutHint
    ): Boolean =
        chineseCandidatePipeline.offsetBulkFilteredPage(delta, layoutHint)

    fun moveChineseBulkFilteredCursor(index: Int): T9PagedCandidates? =
        chineseCandidatePipeline.moveBulkFilteredCursor(index)

    fun filterChinesePagedByReadingPrefixes(
        data: FcitxEvent.PagedCandidateEvent.Data,
        prefixes: List<String>
    ): Pair<T9PagedCandidates, String?> =
        chineseCandidatePipeline.filterPagedByReadingPrefixes(data, prefixes)

    fun buildChineseLocalBudgetedPagedFromCurrentPage(
        source: T9PagedCandidates
    ): T9PagedCandidates? =
        chineseCandidatePipeline.buildLocalBudgetedPagedFromCurrentPage(source)

    fun offsetChineseLocalBudgetedPage(delta: Int): Boolean =
        chineseCandidatePipeline.offsetLocalBudgetedPage(delta)

    fun applyChineseHanziCursor(
        data: FcitxEvent.PagedCandidateEvent.Data,
        cursorContextSignature: String
    ): FcitxEvent.PagedCandidateEvent.Data =
        chineseCandidatePipeline.applyHanziCursor(data, cursorContextSignature)

    fun moveChineseHanziCursor(
        data: FcitxEvent.PagedCandidateEvent.Data,
        index: Int
    ): FcitxEvent.PagedCandidateEvent.Data? =
        chineseCandidatePipeline.moveHanziCursor(data, index)

    fun buildChineseCursorContextSignature(preedit: CharSequence, prefixes: List<String>): String =
        chineseCandidatePipeline.buildCursorContextSignature(preedit, prefixes)

    fun buildSmartEnglishPaged(snapshot: SmartEnglishUiSnapshot): T9PagedCandidates? =
        snapshot.paged?.let { data ->
            smartEnglishPageCache.build(data, snapshot.contentKey)
        }

    fun buildPendingPunctuationPaged(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates {
        val signature = T9CandidateSnapshots.pagerContent(data, characterBudget(), widthBudget())
        pendingPunctuationPager.update(
            signature,
            data.candidates.withIndex().toList(),
            characterBudget(),
            widthBudget()
        )
        val selectedIndex = data.cursorIndex.coerceIn(data.candidates.indices)
        val page = pendingPunctuationPager.selectPageContainingOriginalIndex(selectedIndex)
            ?: return T9PagedCandidates.passthrough(data)
        return page.toPagedCandidates(
            layoutHint = data.layoutHint,
            cursorIndex = page.cursorIndexForOriginalIndex(selectedIndex)
        )
    }

    fun updateShownState(
        source: T9CandidateUiSnapshotPipeline.ShownSource,
        paged: FcitxEvent.PagedCandidateEvent.Data,
        originalIndices: IntArray,
        matchedPrefix: String?
    ): T9CandidateUiSnapshotPipeline.ShownSnapshot {
        return T9CandidateUiSnapshotPipeline.ShownSnapshot(
            source = source,
            paged = paged,
            originalIndices = originalIndices,
            matchedPrefix = matchedPrefix
        ).also {
            currentShown = it
        }
    }

    fun smartEnglishShortcutOriginalIndex(shownIndex: Int): Int? {
        val shown = currentShown
            ?.takeIf { it.source == T9CandidateUiSnapshotPipeline.ShownSource.SMART_ENGLISH }
            ?: return null
        return shown.originalIndexAt(shownIndex)
    }

    fun pendingPunctuationShortcutOriginalIndex(shownIndex: Int): Int? {
        val shown = currentShown
            ?.takeIf { it.source == T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION }
            ?: return null
        return shown.originalIndexAt(shownIndex)
    }

    fun moveCurrentBottomCandidate(delta: Int): T9CandidateUiSnapshotPipeline.MoveBottomCandidate? {
        val shown = currentShown ?: return null
        if (shown.source == T9CandidateUiSnapshotPipeline.ShownSource.OTHER ||
            shown.paged.candidates.isEmpty()
        ) return null
        val next = shown.paged.cursorIndex + delta
        return when {
            next in shown.paged.candidates.indices -> moveWithinCurrentPage(shown, next)
            next >= shown.paged.candidates.size && shown.paged.hasNext ->
                offsetCurrentPage(1)?.toMoveResult()
            next < 0 && shown.paged.hasPrev ->
                offsetCurrentPage(-1)?.toMoveResult()
            else -> null
        }
    }

    fun offsetCurrentPage(delta: Int): T9CandidateUiSnapshotPipeline.PageOffset? =
        when (currentShown?.source) {
            T9CandidateUiSnapshotPipeline.ShownSource.SMART_ENGLISH -> {
                val page = smartEnglishPageCache.offset(delta) ?: return null
                val nextOriginalIndex = page.candidates.firstOrNull()?.index ?: return null
                T9CandidateUiSnapshotPipeline.PageOffset.SmartEnglish(nextOriginalIndex)
            }
            T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION -> {
                val page = pendingPunctuationPager.offset(delta) ?: return null
                val shown = page.toPagedCandidates(
                    layoutHint = currentShown?.paged?.layoutHint
                        ?: FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
                    cursorIndex = page.cursorIndexForOriginalIndex(
                        page.originalIndices.firstOrNull() ?: -1
                    )
                )
                currentShown = T9CandidateUiSnapshotPipeline.ShownSnapshot(
                    source = T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION,
                    paged = shown.data,
                    originalIndices = shown.originalIndices,
                    matchedPrefix = null
                )
                T9CandidateUiSnapshotPipeline.PageOffset.PendingPunctuation(
                    previewOriginalIndex = shown.originalIndices.firstOrNull()
                )
            }
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_BULK -> {
                val shown = currentShown ?: return null
                if (!offsetChineseBulkFilteredPage(delta, shown.paged.layoutHint)) return null
                val state = chineseBulkFilterState
                val nextShown = state.paged ?: return null
                currentShown = shown.copy(
                    paged = nextShown.data,
                    originalIndices = nextShown.originalIndices,
                    matchedPrefix = state.matchedPrefix
                )
                T9CandidateUiSnapshotPipeline.PageOffset.Refresh
            }
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_LOCAL -> {
                val shown = currentShown ?: return null
                if (offsetChineseLocalBudgetedPage(delta)) {
                    T9CandidateUiSnapshotPipeline.PageOffset.Refresh
                } else {
                    val canOffsetEngine = if (delta > 0) shown.paged.hasNext else shown.paged.hasPrev
                    if (canOffsetEngine) {
                        T9CandidateUiSnapshotPipeline.PageOffset.ChineseEngine(delta)
                    } else {
                        null
                    }
                }
            }
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_ENGINE -> {
                val shown = currentShown ?: return null
                val canOffset = if (delta > 0) shown.paged.hasNext else shown.paged.hasPrev
                T9CandidateUiSnapshotPipeline.PageOffset.ChineseEngine(delta).takeIf { canOffset }
            }
            T9CandidateUiSnapshotPipeline.ShownSource.OTHER, null -> null
        }

    fun commitCurrentBottomCandidate(): T9CandidateUiSnapshotPipeline.CommitBottomCandidate? {
        val shown = currentShown ?: return null
        return commitBottomCandidateAt(shown, shown.paged.cursorIndex)
    }

    fun commitBottomCandidateAt(shownIndex: Int): T9CandidateUiSnapshotPipeline.CommitBottomCandidate? {
        val shown = currentShown ?: return null
        return commitBottomCandidateAt(shown, shownIndex)
    }

    private fun commitBottomCandidateAt(
        shown: T9CandidateUiSnapshotPipeline.ShownSnapshot,
        shownIndex: Int
    ): T9CandidateUiSnapshotPipeline.CommitBottomCandidate? {
        if (shownIndex !in shown.paged.candidates.indices) return null
        val originalIndex = shown.originalIndexAt(shownIndex) ?: shownIndex
        return when (shown.source) {
            T9CandidateUiSnapshotPipeline.ShownSource.SMART_ENGLISH ->
                T9CandidateUiSnapshotPipeline.CommitBottomCandidate.SmartEnglish(originalIndex)
            T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION ->
                T9CandidateUiSnapshotPipeline.CommitBottomCandidate.PendingPunctuation(originalIndex)
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_BULK,
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_LOCAL,
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_ENGINE -> {
                val candidate = shown.paged.candidates.getOrNull(shownIndex) ?: return null
                T9CandidateUiSnapshotPipeline.CommitBottomCandidate.Chinese(
                    originalIndex = originalIndex,
                    candidate = candidate,
                    matchedPrefix = shown.matchedPrefix,
                    fromAllCandidates = shown.source ==
                        T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_BULK
                )
            }
            T9CandidateUiSnapshotPipeline.ShownSource.OTHER -> null
        }
    }

    private fun moveWithinCurrentPage(
        shown: T9CandidateUiSnapshotPipeline.ShownSnapshot,
        next: Int
    ): T9CandidateUiSnapshotPipeline.MoveBottomCandidate? {
        val originalIndex = shown.originalIndexAt(next) ?: next
        return when (shown.source) {
            T9CandidateUiSnapshotPipeline.ShownSource.SMART_ENGLISH -> {
                currentShown = shown.copy(paged = shown.paged.copy(cursorIndex = next))
                T9CandidateUiSnapshotPipeline.MoveBottomCandidate.LocalSelection(
                    source = shown.source,
                    originalIndex = originalIndex
                )
            }
            T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION -> {
                val nextPaged = shown.paged.copy(cursorIndex = next)
                currentShown = shown.copy(paged = nextPaged)
                T9CandidateUiSnapshotPipeline.MoveBottomCandidate.LocalSelection(
                    source = shown.source,
                    originalIndex = originalIndex
                )
            }
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_BULK -> {
                val nextShown = moveChineseBulkFilteredCursor(next)
                    ?: T9PagedCandidates(shown.paged.copy(cursorIndex = next), shown.originalIndices)
                currentShown = shown.copy(
                    paged = nextShown.data,
                    originalIndices = nextShown.originalIndices
                )
                T9CandidateUiSnapshotPipeline.MoveBottomCandidate.LocalSelection(
                    source = shown.source,
                    originalIndex = originalIndex
                )
            }
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_LOCAL,
            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_ENGINE -> {
                val nextPaged = moveChineseHanziCursor(shown.paged, next) ?: return null
                currentShown = shown.copy(paged = nextPaged)
                T9CandidateUiSnapshotPipeline.MoveBottomCandidate.LocalSelection(
                    source = shown.source,
                    originalIndex = originalIndex
                )
            }
            T9CandidateUiSnapshotPipeline.ShownSource.OTHER -> null
        }
    }

    private fun T9CandidateUiSnapshotPipeline.PageOffset.toMoveResult():
        T9CandidateUiSnapshotPipeline.MoveBottomCandidate =
        T9CandidateUiSnapshotPipeline.MoveBottomCandidate.PageTransition(this)
}

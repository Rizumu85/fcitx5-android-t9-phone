/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9CandidateUiSnapshotPipeline(
    private val characterBudget: () -> Int,
    private val widthBudget: () -> T9CandidateWidthBudget?,
    candidateMatchesPrefix: (candidate: FcitxEvent.Candidate, prefix: String) -> Boolean = { _, _ -> false }
) {
    enum class ShownSource {
        SMART_ENGLISH,
        PENDING_PUNCTUATION,
        CHINESE_BULK,
        OTHER
    }

    data class ShownSnapshot(
        val source: ShownSource,
        val paged: FcitxEvent.PagedCandidateEvent.Data,
        val originalIndices: IntArray,
        val matchedPrefix: String?
    ) {
        val ownsPagingState: Boolean
            get() = source == ShownSource.SMART_ENGLISH ||
                source == ShownSource.PENDING_PUNCTUATION ||
                source == ShownSource.CHINESE_BULK

        fun originalIndexAt(shownIndex: Int): Int? =
            originalIndices.getOrNull(shownIndex)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ShownSnapshot) return false
            return source == other.source &&
                paged == other.paged &&
                originalIndices.contentEquals(other.originalIndices) &&
                matchedPrefix == other.matchedPrefix
        }

        override fun hashCode(): Int {
            var result = source.hashCode()
            result = 31 * result + paged.hashCode()
            result = 31 * result + originalIndices.contentHashCode()
            result = 31 * result + matchedPrefix.hashCode()
            return result
        }
    }

    sealed class PageOffset {
        data class SmartEnglish(val nextOriginalIndex: Int) : PageOffset()
        data class PendingPunctuation(
            val shown: T9PagedCandidates,
            val previewOriginalIndex: Int?
        ) : PageOffset()
        data class ChineseBulk(val shown: T9PagedCandidates) : PageOffset()
    }

    sealed class MoveBottomCandidate {
        data class SmartEnglish(val nextOriginalIndex: Int) : MoveBottomCandidate()
        data class PendingPunctuation(
            val shown: T9PagedCandidates,
            val previewOriginalIndex: Int
        ) : MoveBottomCandidate()
        data class ChineseBulk(val shown: T9PagedCandidates) : MoveBottomCandidate()
    }

    sealed class CommitBottomCandidate {
        data class SmartEnglish(val originalIndex: Int) : CommitBottomCandidate()
        data class PendingPunctuation(val originalIndex: Int) : CommitBottomCandidate()
        data class ChineseBulk(
            val originalIndex: Int,
            val candidate: FcitxEvent.Candidate,
            val matchedPrefix: String?
        ) : CommitBottomCandidate()
    }

    private val smartEnglishPageCache = T9SmartEnglishPageCache(characterBudget, widthBudget)
    private val pendingPunctuationPager = T9CandidatePager()
    private val chineseCandidatePipeline = ChineseT9CandidatePipeline(
        characterBudget = characterBudget,
        widthBudget = widthBudget,
        candidateMatchesPrefix = candidateMatchesPrefix
    )
    private val pinyinRowWindow = T9PinyinRowWindow()
    private var currentShown: ShownSnapshot? = null

    val shownSource: ShownSource
        get() = currentShown?.source ?: ShownSource.OTHER

    val ownsCurrentShownState: Boolean
        get() = currentShown?.ownsPagingState == true

    val hasCurrentBottomCandidateRow: Boolean
        get() = currentShown?.paged?.candidates?.isNotEmpty() == true

    val currentShownMatchedPrefix: String?
        get() = currentShown?.matchedPrefix

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
        pinyinRowWindow.clear()
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

    fun filterChinesePagedByPinyinPrefixes(
        data: FcitxEvent.PagedCandidateEvent.Data,
        prefixes: List<String>
    ): Pair<T9PagedCandidates, String?> =
        chineseCandidatePipeline.filterPagedByPinyinPrefixes(data, prefixes)

    fun buildChineseLocalBudgetedPagedFromCurrentPage(
        data: FcitxEvent.PagedCandidateEvent.Data
    ): T9PagedCandidates? =
        chineseCandidatePipeline.buildLocalBudgetedPagedFromCurrentPage(data)

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

    fun clearPinyinWindow() {
        pinyinRowWindow.clear()
    }

    fun submitPinyinWindow(candidates: List<String>): T9PinyinRowWindow.VisibleState =
        pinyinRowWindow.submit(candidates)

    fun movePinyinWindow(delta: Int): T9PinyinRowWindow.VisibleState? =
        pinyinRowWindow.move(delta)

    fun resetPinyinHighlight(): T9PinyinRowWindow.VisibleState? =
        pinyinRowWindow.resetHighlight()

    fun currentPinyinWindowState(): T9PinyinRowWindow.VisibleState? =
        pinyinRowWindow.currentState()

    fun highlightedPinyin(): String? =
        pinyinRowWindow.highlightedPinyin()

    fun buildSmartEnglishPaged(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates =
        smartEnglishPageCache.build(data)

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
        paged: FcitxEvent.PagedCandidateEvent.Data,
        originalIndices: IntArray,
        usesSmartEnglish: Boolean,
        usesPendingPunctuation: Boolean,
        usesBulkSelection: Boolean,
        matchedPrefix: String?
    ): ShownSnapshot {
        val source = when {
            usesPendingPunctuation -> ShownSource.PENDING_PUNCTUATION
            usesSmartEnglish -> ShownSource.SMART_ENGLISH
            usesBulkSelection -> ShownSource.CHINESE_BULK
            else -> ShownSource.OTHER
        }
        return ShownSnapshot(
            source = source,
            paged = paged,
            originalIndices = originalIndices,
            matchedPrefix = matchedPrefix
        ).also {
            currentShown = it
        }
    }

    fun smartEnglishShortcutOriginalIndex(shownIndex: Int): Int? {
        val shown = currentShown?.takeIf { it.source == ShownSource.SMART_ENGLISH } ?: return null
        return shown.originalIndexAt(shownIndex)
    }

    fun pendingPunctuationShortcutOriginalIndex(shownIndex: Int): Int? {
        val shown = currentShown?.takeIf { it.source == ShownSource.PENDING_PUNCTUATION } ?: return null
        return shown.originalIndexAt(shownIndex)
    }

    fun moveCurrentBottomCandidate(delta: Int): MoveBottomCandidate? {
        val shown = currentShown ?: return null
        if (!shown.ownsPagingState || shown.paged.candidates.isEmpty()) return null
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

    fun offsetCurrentPage(delta: Int): PageOffset? =
        when (currentShown?.source) {
            ShownSource.SMART_ENGLISH -> {
                val page = smartEnglishPageCache.offset(delta) ?: return null
                val nextOriginalIndex = page.candidates.firstOrNull()?.index ?: return null
                PageOffset.SmartEnglish(nextOriginalIndex)
            }
            ShownSource.PENDING_PUNCTUATION -> {
                val page = pendingPunctuationPager.offset(delta) ?: return null
                val shown = page.toPagedCandidates(
                    layoutHint = currentShown?.paged?.layoutHint
                        ?: FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
                    cursorIndex = page.cursorIndexForOriginalIndex(
                        page.originalIndices.firstOrNull() ?: -1
                    )
                )
                currentShown = ShownSnapshot(
                    source = ShownSource.PENDING_PUNCTUATION,
                    paged = shown.data,
                    originalIndices = shown.originalIndices,
                    matchedPrefix = null
                )
                PageOffset.PendingPunctuation(
                    shown = shown,
                    previewOriginalIndex = shown.originalIndices.firstOrNull()
                )
            }
            ShownSource.CHINESE_BULK -> {
                val shown = currentShown ?: return null
                if (!offsetChineseBulkFilteredPage(delta, shown.paged.layoutHint)) return null
                val state = chineseBulkFilterState
                val nextShown = state.paged ?: return null
                currentShown = shown.copy(
                    paged = nextShown.data,
                    originalIndices = nextShown.originalIndices,
                    matchedPrefix = state.matchedPrefix
                )
                PageOffset.ChineseBulk(nextShown)
            }
            else -> null
        }

    fun commitCurrentBottomCandidate(): CommitBottomCandidate? {
        val shown = currentShown ?: return null
        if (!shown.ownsPagingState) return null
        return commitBottomCandidateAt(shown, shown.paged.cursorIndex)
    }

    fun commitBottomCandidateAt(shownIndex: Int): CommitBottomCandidate? {
        val shown = currentShown ?: return null
        if (!shown.ownsPagingState) return null
        return commitBottomCandidateAt(shown, shownIndex)
    }

    private fun commitBottomCandidateAt(
        shown: ShownSnapshot,
        shownIndex: Int
    ): CommitBottomCandidate? {
        if (shownIndex !in shown.paged.candidates.indices) return null
        val originalIndex = shown.originalIndexAt(shownIndex) ?: shownIndex
        return when (shown.source) {
            ShownSource.SMART_ENGLISH -> CommitBottomCandidate.SmartEnglish(originalIndex)
            ShownSource.PENDING_PUNCTUATION -> CommitBottomCandidate.PendingPunctuation(originalIndex)
            ShownSource.CHINESE_BULK -> {
                val candidate = shown.paged.candidates.getOrNull(shownIndex) ?: return null
                CommitBottomCandidate.ChineseBulk(
                    originalIndex = originalIndex,
                    candidate = candidate,
                    matchedPrefix = shown.matchedPrefix
                )
            }
            ShownSource.OTHER -> null
        }
    }

    private fun moveWithinCurrentPage(
        shown: ShownSnapshot,
        next: Int
    ): MoveBottomCandidate? {
        val originalIndex = shown.originalIndexAt(next) ?: next
        return when (shown.source) {
            ShownSource.SMART_ENGLISH -> MoveBottomCandidate.SmartEnglish(originalIndex)
            ShownSource.PENDING_PUNCTUATION -> {
                val nextPaged = shown.paged.copy(cursorIndex = next)
                currentShown = shown.copy(paged = nextPaged)
                MoveBottomCandidate.PendingPunctuation(
                    shown = T9PagedCandidates(nextPaged, shown.originalIndices),
                    previewOriginalIndex = originalIndex
                )
            }
            ShownSource.CHINESE_BULK -> {
                val nextPaged = shown.paged.copy(cursorIndex = next)
                currentShown = shown.copy(paged = nextPaged)
                MoveBottomCandidate.ChineseBulk(T9PagedCandidates(nextPaged, shown.originalIndices))
            }
            ShownSource.OTHER -> null
        }
    }

    private fun PageOffset.toMoveResult(): MoveBottomCandidate =
        when (this) {
            is PageOffset.SmartEnglish ->
                MoveBottomCandidate.SmartEnglish(nextOriginalIndex)
            is PageOffset.PendingPunctuation ->
                MoveBottomCandidate.PendingPunctuation(
                    shown = shown,
                    previewOriginalIndex = previewOriginalIndex ?: -1
                )
            is PageOffset.ChineseBulk ->
                MoveBottomCandidate.ChineseBulk(shown)
        }
}

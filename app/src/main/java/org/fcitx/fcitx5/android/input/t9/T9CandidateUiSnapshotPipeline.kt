/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9CandidateUiSnapshotPipeline(
    characterBudget: () -> Int,
    widthBudget: () -> T9CandidateWidthBudget?,
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

    private val sourceSessions = T9CandidateSourceSessions(
        characterBudget = characterBudget,
        widthBudget = widthBudget,
        candidateMatchesPrefix = candidateMatchesPrefix
    )
    private val pinyinRowWindow = T9PinyinRowWindow()

    val shownSource: ShownSource
        get() = sourceSessions.shownSource

    val ownsCurrentShownState: Boolean
        get() = sourceSessions.ownsCurrentShownState

    val hasCurrentBottomCandidateRow: Boolean
        get() = sourceSessions.hasCurrentBottomCandidateRow

    val currentShownMatchedPrefix: String?
        get() = sourceSessions.currentShownMatchedPrefix

    val hasChineseLocalBudgetCandidates: Boolean
        get() = sourceSessions.hasChineseLocalBudgetCandidates

    val hasChineseBulkFilteredCandidates: Boolean
        get() = sourceSessions.hasChineseBulkFilteredCandidates

    val chineseBulkFilterState: ChineseT9CandidatePipeline.BulkFilterState
        get() = sourceSessions.chineseBulkFilterState

    fun reset() {
        sourceSessions.reset()
        pinyinRowWindow.clear()
    }

    fun resetChineseLocalBudgetState() {
        sourceSessions.resetChineseLocalBudgetState()
    }

    fun resetChineseBulkFilterState() {
        sourceSessions.resetChineseBulkFilterState()
    }

    fun chineseBulkFilterRequestSignature(
        prefixes: List<String>,
        preedit: CharSequence,
        candidates: Array<FcitxEvent.Candidate>
    ): String =
        sourceSessions.chineseBulkFilterRequestSignature(prefixes, preedit, candidates)

    fun shouldRequestChineseBulkFilter(signature: String): Boolean =
        sourceSessions.shouldRequestChineseBulkFilter(signature)

    fun startChineseBulkFilterRequest(prefixes: List<String>, signature: String) {
        sourceSessions.startChineseBulkFilterRequest(prefixes, signature)
    }

    fun finishChineseBulkFilterRequest(
        signature: String,
        rawCandidates: List<String>,
        prefixes: List<String>,
        layoutHint: FcitxEvent.PagedCandidateEvent.LayoutHint
    ): ChineseT9CandidatePipeline.BulkFilterState? =
        sourceSessions.finishChineseBulkFilterRequest(
            signature = signature,
            rawCandidates = rawCandidates,
            prefixes = prefixes,
            layoutHint = layoutHint
        )

    fun offsetChineseBulkFilteredPage(
        delta: Int,
        layoutHint: FcitxEvent.PagedCandidateEvent.LayoutHint
    ): Boolean =
        sourceSessions.offsetChineseBulkFilteredPage(delta, layoutHint)

    fun moveChineseBulkFilteredCursor(index: Int): T9PagedCandidates? =
        sourceSessions.moveChineseBulkFilteredCursor(index)

    fun filterChinesePagedByPinyinPrefixes(
        data: FcitxEvent.PagedCandidateEvent.Data,
        prefixes: List<String>
    ): Pair<T9PagedCandidates, String?> =
        sourceSessions.filterChinesePagedByPinyinPrefixes(data, prefixes)

    fun buildChineseLocalBudgetedPagedFromCurrentPage(
        data: FcitxEvent.PagedCandidateEvent.Data
    ): T9PagedCandidates? =
        sourceSessions.buildChineseLocalBudgetedPagedFromCurrentPage(data)

    fun offsetChineseLocalBudgetedPage(delta: Int): Boolean =
        sourceSessions.offsetChineseLocalBudgetedPage(delta)

    fun applyChineseHanziCursor(
        data: FcitxEvent.PagedCandidateEvent.Data,
        cursorContextSignature: String
    ): FcitxEvent.PagedCandidateEvent.Data =
        sourceSessions.applyChineseHanziCursor(data, cursorContextSignature)

    fun moveChineseHanziCursor(
        data: FcitxEvent.PagedCandidateEvent.Data,
        index: Int
    ): FcitxEvent.PagedCandidateEvent.Data? =
        sourceSessions.moveChineseHanziCursor(data, index)

    fun buildChineseCursorContextSignature(preedit: CharSequence, prefixes: List<String>): String =
        sourceSessions.buildChineseCursorContextSignature(preedit, prefixes)

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
        sourceSessions.buildSmartEnglishPaged(data)

    fun buildPendingPunctuationPaged(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates =
        sourceSessions.buildPendingPunctuationPaged(data)

    fun updateShownState(
        paged: FcitxEvent.PagedCandidateEvent.Data,
        originalIndices: IntArray,
        usesSmartEnglish: Boolean,
        usesPendingPunctuation: Boolean,
        usesBulkSelection: Boolean,
        matchedPrefix: String?
    ): ShownSnapshot {
        return sourceSessions.updateShownState(
            paged = paged,
            originalIndices = originalIndices,
            usesSmartEnglish = usesSmartEnglish,
            usesPendingPunctuation = usesPendingPunctuation,
            usesBulkSelection = usesBulkSelection,
            matchedPrefix = matchedPrefix
        )
    }

    fun smartEnglishShortcutOriginalIndex(shownIndex: Int): Int? =
        sourceSessions.smartEnglishShortcutOriginalIndex(shownIndex)

    fun pendingPunctuationShortcutOriginalIndex(shownIndex: Int): Int? =
        sourceSessions.pendingPunctuationShortcutOriginalIndex(shownIndex)

    fun moveCurrentBottomCandidate(delta: Int): MoveBottomCandidate? =
        sourceSessions.moveCurrentBottomCandidate(delta)

    fun offsetCurrentPage(delta: Int): PageOffset? =
        sourceSessions.offsetCurrentPage(delta)

    fun commitCurrentBottomCandidate(): CommitBottomCandidate? {
        return sourceSessions.commitCurrentBottomCandidate()
    }

    fun commitBottomCandidateAt(shownIndex: Int): CommitBottomCandidate? {
        return sourceSessions.commitBottomCandidateAt(shownIndex)
    }
}

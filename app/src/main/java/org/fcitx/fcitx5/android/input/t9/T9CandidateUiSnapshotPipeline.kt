/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9CandidateUiSnapshotPipeline(
    characterBudget: () -> Int,
    widthBudget: () -> T9CandidateWidthBudget?,
    candidateMatchesPrefix: (candidate: FcitxEvent.Candidate, prefix: String) -> Boolean = { _, _ -> false },
    private val requestBulkCandidates: (chineseT9Active: Boolean, prefixes: List<String>) -> Unit = { _, _ -> },
    private val getPresentationState: (ChineseT9PresentationSnapshotKey) -> T9PresentationState = {
        T9PresentationState(topReading = null, pinyinOptions = emptyList())
    },
    private val clearHiddenComposition: () -> Unit = {}
) {
    enum class ShownSource {
        SMART_ENGLISH,
        PENDING_PUNCTUATION,
        CHINESE_BULK,
        CHINESE_LOCAL,
        CHINESE_ENGINE,
        OTHER
    }

    data class ShownSnapshot(
        val source: ShownSource,
        val paged: FcitxEvent.PagedCandidateEvent.Data,
        val originalIndices: IntArray,
        val matchedPrefix: String?
    ) {
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

    data class ChineseSelectionTicket(
        val source: ShownSource,
        val shownIndex: Int,
        val originalIndex: Int,
        val candidate: FcitxEvent.Candidate
    )

    sealed class PageOffset {
        data class SmartEnglish(val nextOriginalIndex: Int) : PageOffset()
        data class PendingPunctuation(val previewOriginalIndex: Int?) : PageOffset()
        object Refresh : PageOffset()
        data class ChineseEngine(val delta: Int) : PageOffset()
    }

    sealed class MoveBottomCandidate {
        data class SmartEnglish(val nextOriginalIndex: Int) : MoveBottomCandidate()
        data class PendingPunctuation(val previewOriginalIndex: Int) : MoveBottomCandidate()
        object Refresh : MoveBottomCandidate()
        data class ChineseEngine(val delta: Int) : MoveBottomCandidate()
    }

    sealed class CommitBottomCandidate {
        data class SmartEnglish(val originalIndex: Int) : CommitBottomCandidate()
        data class PendingPunctuation(val originalIndex: Int) : CommitBottomCandidate()
        data class Chinese(
            val originalIndex: Int,
            val candidate: FcitxEvent.Candidate,
            val matchedPrefix: String?,
            val fromAllCandidates: Boolean
        ) : CommitBottomCandidate()
    }

    private val sourceSessions = T9CandidateSourceSessions(
        characterBudget = characterBudget,
        widthBudget = widthBudget,
        candidateMatchesPrefix = candidateMatchesPrefix
    )
    private val pinyinRowWindow = T9PinyinRowWindow()
    private val stateBuilder = T9CandidateUiStateBuilder(object : T9CandidateUiStateBuilder.Pipeline {
        override fun buildSmartEnglishPaged(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates =
            sourceSessions.buildSmartEnglishPaged(data)

        override fun buildT9PendingPunctuationPaged(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates = sourceSessions.buildPendingPunctuationPaged(data)

        override fun resetT9BulkFilterState() {
            sourceSessions.resetChineseBulkFilterState()
        }

        override fun requestT9BulkFilteredCandidatesIfNeeded(
            chineseT9Active: Boolean,
            prefixes: List<String>
        ) {
            requestBulkCandidates(chineseT9Active, prefixes)
        }

        override fun getT9BulkFilterState(): ChineseT9CandidatePipeline.BulkFilterState =
            sourceSessions.chineseBulkFilterState

        override fun filterPagedByT9PinyinPrefixes(
            data: FcitxEvent.PagedCandidateEvent.Data,
            prefixes: List<String>
        ): Pair<T9PagedCandidates, String?> =
            sourceSessions.filterChinesePagedByPinyinPrefixes(data, prefixes)

        override fun buildLocalBudgetedPagedFromCurrentPage(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates? = sourceSessions.buildChineseLocalBudgetedPagedFromCurrentPage(data)

        override fun resetT9LocalBudgetState() {
            sourceSessions.resetChineseLocalBudgetState()
        }

        override fun buildT9CursorContextSignature(
            preedit: CharSequence,
            prefixes: List<String>
        ): String = sourceSessions.buildChineseCursorContextSignature(preedit, prefixes)

        override fun applyT9HanziCursor(
            data: FcitxEvent.PagedCandidateEvent.Data,
            cursorContextSignature: String
        ): FcitxEvent.PagedCandidateEvent.Data =
            sourceSessions.applyChineseHanziCursor(data, cursorContextSignature)

        override fun getT9PresentationState(key: ChineseT9PresentationSnapshotKey): T9PresentationState =
            getPresentationState.invoke(key)

        override fun clearHiddenChineseT9CompositionIfCandidateUiSuppressed() {
            clearHiddenComposition.invoke()
        }
    })

    val shownSource: ShownSource
        get() = sourceSessions.shownSource

    val currentShownSnapshot: ShownSnapshot?
        get() = sourceSessions.currentShownSnapshot

    val hasCurrentBottomCandidateRow: Boolean
        get() = sourceSessions.hasCurrentBottomCandidateRow

    val currentShownMatchedPrefix: String?
        get() = sourceSessions.currentShownMatchedPrefix

    fun currentChineseSelectionTicket(
        originalIndex: Int,
        candidate: FcitxEvent.Candidate
    ): ChineseSelectionTicket? =
        sourceSessions.currentChineseSelectionTicket(originalIndex, candidate)

    val hasChineseLocalBudgetCandidates: Boolean
        get() = sourceSessions.hasChineseLocalBudgetCandidates

    val hasChineseBulkFilteredCandidates: Boolean
        get() = sourceSessions.hasChineseBulkFilteredCandidates

    val chineseBulkFilterState: ChineseT9CandidatePipeline.BulkFilterState
        get() = sourceSessions.chineseBulkFilterState

    fun build(input: T9CandidateUiInputSnapshot): T9CandidateUiSnapshot? =
        stateBuilder.build(input)?.also { snapshot ->
            sourceSessions.updateShownState(
                source = snapshot.interactionState.shownSource,
                paged = snapshot.shownState.paged,
                originalIndices = snapshot.shownState.originalIndices,
                matchedPrefix = snapshot.shownState.matchedPrefix
            )
        }

    fun reset() {
        sourceSessions.reset()
        pinyinRowWindow.clear()
    }

    fun invalidateShownInteraction() {
        sourceSessions.invalidateShownInteraction()
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
        source: ShownSource,
        paged: FcitxEvent.PagedCandidateEvent.Data,
        originalIndices: IntArray,
        matchedPrefix: String?
    ): ShownSnapshot {
        return sourceSessions.updateShownState(
            source = source,
            paged = paged,
            originalIndices = originalIndices,
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

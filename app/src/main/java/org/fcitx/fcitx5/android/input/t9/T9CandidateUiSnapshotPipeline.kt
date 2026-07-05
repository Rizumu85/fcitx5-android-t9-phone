/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9CandidateUiSnapshotPipeline(
    private val characterBudget: () -> Int,
    private val widthBudget: () -> T9CandidateWidthBudget?
) {
    enum class ShownSource {
        SMART_ENGLISH,
        PENDING_PUNCTUATION,
        OTHER
    }

    data class ShownSnapshot(
        val source: ShownSource,
        val paged: FcitxEvent.PagedCandidateEvent.Data,
        val originalIndices: IntArray
    ) {
        val ownsPagingState: Boolean
            get() = source == ShownSource.SMART_ENGLISH ||
                source == ShownSource.PENDING_PUNCTUATION

        fun originalIndexAt(shownIndex: Int): Int? =
            originalIndices.getOrNull(shownIndex)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ShownSnapshot) return false
            return source == other.source &&
                paged == other.paged &&
                originalIndices.contentEquals(other.originalIndices)
        }

        override fun hashCode(): Int {
            var result = source.hashCode()
            result = 31 * result + paged.hashCode()
            result = 31 * result + originalIndices.contentHashCode()
            return result
        }
    }

    sealed class PageOffset {
        data class SmartEnglish(val nextOriginalIndex: Int) : PageOffset()
        data class PendingPunctuation(
            val shown: T9PagedCandidates,
            val previewOriginalIndex: Int?
        ) : PageOffset()
    }

    sealed class MoveBottomCandidate {
        data class SmartEnglish(val nextOriginalIndex: Int) : MoveBottomCandidate()
        data class PendingPunctuation(
            val shown: T9PagedCandidates,
            val previewOriginalIndex: Int
        ) : MoveBottomCandidate()
    }

    sealed class CommitBottomCandidate {
        data class SmartEnglish(val originalIndex: Int) : CommitBottomCandidate()
        data class PendingPunctuation(val originalIndex: Int) : CommitBottomCandidate()
    }

    private val smartEnglishPageCache = T9SmartEnglishPageCache(characterBudget, widthBudget)
    private val pendingPunctuationPager = T9CandidatePager()
    private var currentShown: ShownSnapshot? = null

    val shownSource: ShownSource
        get() = currentShown?.source ?: ShownSource.OTHER

    val ownsCurrentShownState: Boolean
        get() = currentShown?.ownsPagingState == true

    fun reset() {
        smartEnglishPageCache.reset()
        pendingPunctuationPager.reset()
        currentShown = null
    }

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
        usesPendingPunctuation: Boolean
    ): ShownSnapshot {
        val source = when {
            usesPendingPunctuation -> ShownSource.PENDING_PUNCTUATION
            usesSmartEnglish -> ShownSource.SMART_ENGLISH
            else -> ShownSource.OTHER
        }
        return ShownSnapshot(
            source = source,
            paged = paged,
            originalIndices = originalIndices
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
                    originalIndices = shown.originalIndices
                )
                PageOffset.PendingPunctuation(
                    shown = shown,
                    previewOriginalIndex = shown.originalIndices.firstOrNull()
                )
            }
            else -> null
        }

    fun commitCurrentBottomCandidate(): CommitBottomCandidate? {
        val shown = currentShown ?: return null
        if (!shown.ownsPagingState) return null
        val shownIndex = shown.paged.cursorIndex
        if (shownIndex !in shown.paged.candidates.indices) return null
        val originalIndex = shown.originalIndexAt(shownIndex) ?: shownIndex
        return when (shown.source) {
            ShownSource.SMART_ENGLISH -> CommitBottomCandidate.SmartEnglish(originalIndex)
            ShownSource.PENDING_PUNCTUATION -> CommitBottomCandidate.PendingPunctuation(originalIndex)
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
        }
}

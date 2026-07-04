/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9PagedCandidates(
    val data: FcitxEvent.PagedCandidateEvent.Data,
    val originalIndices: IntArray
) {
    fun withData(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates =
        T9PagedCandidates(data, originalIndices)

    fun withOriginalIndices(originalIndices: IntArray): T9PagedCandidates =
        T9PagedCandidates(data, originalIndices)

    companion object {
        val Empty = T9PagedCandidates(
            FcitxEvent.PagedCandidateEvent.Data.Empty,
            intArrayOf()
        )

        fun passthrough(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates =
            T9PagedCandidates(
                data = data,
                originalIndices = IntArray(data.candidates.size) { it }
            )
    }
}

class T9CandidatePager {

    data class Page(
        val candidates: List<IndexedValue<FcitxEvent.Candidate>>,
        val index: Int,
        val hasPrev: Boolean,
        val hasNext: Boolean
    ) {
        val originalIndices: IntArray
            get() = candidates.map { it.index }.toIntArray()

        fun cursorIndexForOriginalIndex(originalIndex: Int): Int =
            candidates.indexOfFirst { it.index == originalIndex }
                .takeIf { it >= 0 }
                ?: candidates.indices.firstOrNull()
                ?: -1

        fun toPagedCandidates(
            layoutHint: FcitxEvent.PagedCandidateEvent.LayoutHint,
            cursorIndex: Int = 0,
            hasExternalPrev: Boolean = false,
            hasExternalNext: Boolean = false
        ): T9PagedCandidates =
            T9PagedCandidates(
                data = FcitxEvent.PagedCandidateEvent.Data(
                    candidates = candidates.map { it.value }.toTypedArray(),
                    cursorIndex = cursorIndex,
                    layoutHint = layoutHint,
                    hasPrev = hasExternalPrev || hasPrev,
                    hasNext = hasExternalNext || hasNext
                ),
                originalIndices = originalIndices
            )
    }

    private var signature = ""
    private var budget = 0
    private var widthSignature = ""
    private var pages: List<List<IndexedValue<FcitxEvent.Candidate>>> = emptyList()

    var pageIndex = 0
        private set

    var candidates: List<IndexedValue<FcitxEvent.Candidate>> = emptyList()
        private set

    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()

    fun reset() {
        signature = ""
        budget = 0
        widthSignature = ""
        pages = emptyList()
        pageIndex = 0
        candidates = emptyList()
    }

    fun update(
        signature: String,
        candidates: List<IndexedValue<FcitxEvent.Candidate>>,
        characterBudget: Int,
        widthBudget: T9CandidateWidthBudget? = null
    ) {
        val normalizedBudget = T9CandidateBudget.normalizedBudget(characterBudget)
        val normalizedWidthSignature = widthBudget?.signature.orEmpty()
        if (
            this.signature == signature &&
            budget == normalizedBudget &&
            widthSignature == normalizedWidthSignature
        ) return
        this.signature = signature
        this.budget = normalizedBudget
        this.widthSignature = normalizedWidthSignature
        this.candidates = candidates
        pages = buildPages(candidates, normalizedBudget, widthBudget)
        pageIndex = 0
    }

    fun currentPage(): Page? = pageAt(pageIndex)

    fun pageAt(index: Int): Page? {
        if (pages.isEmpty()) return null
        val safeIndex = index.coerceIn(0, pages.lastIndex)
        pageIndex = safeIndex
        return pageFor(safeIndex)
    }

    fun offset(delta: Int): Page? = pageAt(pageIndex + delta)

    fun selectPageContainingOriginalIndex(originalIndex: Int): Page? {
        if (pages.isEmpty()) return null
        val nextIndex = pages.indexOfFirst { page ->
            page.any { it.index == originalIndex }
        }.takeIf { it >= 0 } ?: pageIndex.coerceIn(pages.indices)
        pageIndex = nextIndex
        return pageFor(nextIndex)
    }

    private fun pageFor(index: Int): Page =
        Page(
            candidates = pages[index],
            index = index,
            hasPrev = index > 0,
            hasNext = index < pages.lastIndex
        )

    private fun buildPages(
        candidates: List<IndexedValue<FcitxEvent.Candidate>>,
        budget: Int,
        widthBudget: T9CandidateWidthBudget?
    ): List<List<IndexedValue<FcitxEvent.Candidate>>> {
        if (candidates.isEmpty()) return emptyList()
        val pages = mutableListOf<MutableList<IndexedValue<FcitxEvent.Candidate>>>()
        var current = mutableListOf<IndexedValue<FcitxEvent.Candidate>>()
        var used = 0
        var usedWidth = 0
        candidates.forEach { candidate ->
            val length = T9CandidateBudget.candidateCost(candidate.value.text)
            val width = widthBudget?.candidateWidthPx(candidate.value) ?: 0
            // T9 pages map directly to the physical 1-0 shortcuts, so a page must never expose
            // more candidates than the user can select by number even when short English words
            // would fit the character budget.
            if (current.isNotEmpty() &&
                (
                    used + length > budget ||
                        current.size >= T9CandidateBudget.MAX_CANDIDATES_PER_PAGE ||
                        (widthBudget != null && usedWidth + width > widthBudget.maxWidthPx)
                    )
            ) {
                pages += current
                current = mutableListOf()
                used = 0
                usedWidth = 0
            }
            current += candidate
            used += length
            usedWidth += width
        }
        if (current.isNotEmpty()) {
            pages += current
        }
        return pages
    }
}

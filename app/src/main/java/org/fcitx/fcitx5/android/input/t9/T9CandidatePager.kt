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

    fun indexedCandidates(): List<IndexedValue<FcitxEvent.Candidate>> =
        data.candidates.mapIndexed { shownIndex, candidate ->
            IndexedValue(originalIndices.getOrElse(shownIndex) { shownIndex }, candidate)
        }

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
    private var pinnedFirstPageTailOriginalIndex: Int? = null
    private var avoidSingleCandidateTail = false
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
        pinnedFirstPageTailOriginalIndex = null
        avoidSingleCandidateTail = false
        pages = emptyList()
        pageIndex = 0
        candidates = emptyList()
    }

    fun update(
        signature: String,
        candidates: List<IndexedValue<FcitxEvent.Candidate>>,
        characterBudget: Int,
        widthBudget: T9CandidateWidthBudget? = null,
        pinnedFirstPageTailOriginalIndex: Int? = null,
        avoidSingleCandidateTail: Boolean = false
    ) {
        val normalizedBudget = T9CandidateBudget.normalizedBudget(characterBudget)
        val normalizedWidthSignature = widthBudget?.signature.orEmpty()
        if (
            this.signature == signature &&
            budget == normalizedBudget &&
            widthSignature == normalizedWidthSignature &&
            this.pinnedFirstPageTailOriginalIndex == pinnedFirstPageTailOriginalIndex &&
            this.avoidSingleCandidateTail == avoidSingleCandidateTail
        ) return
        this.signature = signature
        this.budget = normalizedBudget
        this.widthSignature = normalizedWidthSignature
        this.pinnedFirstPageTailOriginalIndex = pinnedFirstPageTailOriginalIndex
        this.avoidSingleCandidateTail = avoidSingleCandidateTail
        this.candidates = candidates
        pages = buildPages(
            candidates,
            normalizedBudget,
            widthBudget,
            pinnedFirstPageTailOriginalIndex,
            avoidSingleCandidateTail
        )
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
        widthBudget: T9CandidateWidthBudget?,
        pinnedFirstPageTailOriginalIndex: Int?,
        avoidSingleCandidateTail: Boolean
    ): List<List<IndexedValue<FcitxEvent.Candidate>>> {
        if (candidates.isEmpty()) return emptyList()
        val pinned = candidates.firstOrNull { it.index == pinnedFirstPageTailOriginalIndex }
        val ordinary = if (pinned == null) candidates else candidates.filterNot { it === pinned }
        val pages = paginate(
            ordinary,
            budget,
            widthBudget,
            avoidSingleCandidateTail
        ).toMutableList()
        if (pinned == null) return pages

        if (pages.isEmpty()) pages.add(mutableListOf())
        val first = pages.first()
        val displaced = mutableListOf<IndexedValue<FcitxEvent.Candidate>>()
        while (first.isNotEmpty() && !pageFits(first + pinned, budget, widthBudget)) {
            displaced.add(0, first.removeAt(first.lastIndex))
        }
        first += pinned
        if (displaced.isNotEmpty()) {
            val remainder = displaced + pages.drop(1).flatten()
            while (pages.size > 1) pages.removeAt(pages.lastIndex)
            pages.addAll(paginate(remainder, budget, widthBudget, avoidSingleCandidateTail))
        }
        return pages
    }

    private fun paginate(
        candidates: List<IndexedValue<FcitxEvent.Candidate>>,
        budget: Int,
        widthBudget: T9CandidateWidthBudget?,
        avoidSingleCandidateTail: Boolean
    ): List<MutableList<IndexedValue<FcitxEvent.Candidate>>> {
        val pages = mutableListOf<MutableList<IndexedValue<FcitxEvent.Candidate>>>()
        var current = mutableListOf<IndexedValue<FcitxEvent.Candidate>>()
        candidates.forEach { candidate ->
            // T9 pages map directly to the physical 1-0 shortcuts, so a page must never expose
            // more candidates than the user can select by number even when short English words
            // would fit the character budget.
            if (current.isNotEmpty() && !pageFits(current + candidate, budget, widthBudget)) {
                pages += current
                current = mutableListOf()
            }
            current += candidate
        }
        if (current.isNotEmpty()) {
            pages += current
        }
        return if (avoidSingleCandidateTail) {
            rebalanceAvoidableSingletonTail(pages, budget, widthBudget)
        } else {
            pages
        }
    }

    private fun rebalanceAvoidableSingletonTail(
        pages: MutableList<MutableList<IndexedValue<FcitxEvent.Candidate>>>,
        budget: Int,
        widthBudget: T9CandidateWidthBudget?
    ): List<MutableList<IndexedValue<FcitxEvent.Candidate>>> {
        if (pages.size < 2 || pages.last().size != 1) return pages
        val previous = pages[pages.lastIndex - 1]
        if (previous.size < 3) return pages
        val moved = previous.last()
        val balancedTail = mutableListOf(moved).apply { addAll(pages.last()) }
        if (!pageFits(balancedTail, budget, widthBudget)) return pages
        // Product decision: preserve a nearly full first page, but do not make the next physical
        // navigation step look like the dictionary collapsed to one result when two fit.
        previous.removeAt(previous.lastIndex)
        pages[pages.lastIndex] = balancedTail
        return pages
    }

    private fun pageFits(
        candidates: List<IndexedValue<FcitxEvent.Candidate>>,
        budget: Int,
        widthBudget: T9CandidateWidthBudget?
    ): Boolean {
        if (candidates.size > T9CandidateBudget.MAX_CANDIDATES_PER_PAGE) return false
        // Character cost keeps the first layout deterministic before Android can measure the row.
        // Once measured geometry exists, retaining both caps leaves visible space while moving
        // candidates to the next page; the renderer's width model is then the only visual budget.
        return if (widthBudget == null) {
            candidates.sumOf { T9CandidateBudget.candidateCost(it.value.text) } <= budget
        } else {
            widthBudget.rowWidthPx(candidates.map { it.value }) <= widthBudget.maxWidthPx
        }
    }
}

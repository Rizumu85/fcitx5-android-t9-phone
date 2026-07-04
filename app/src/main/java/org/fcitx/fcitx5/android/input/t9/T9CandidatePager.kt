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
    data class Budget(
        val value: Int,
        val key: Any = value,
        val itemCost: (FcitxEvent.Candidate) -> Int,
        val hardValue: Int = value,
        val hardItemCost: (FcitxEvent.Candidate) -> Int = itemCost,
        val protectedMinItems: Int = 0,
        val canProtectItem: (FcitxEvent.Candidate) -> Boolean = { false }
    ) {
        companion object {
            fun character(characterBudget: Int): Budget {
                val normalizedBudget = T9CandidateBudget.normalizedBudget(characterBudget)
                return Budget(
                    value = normalizedBudget,
                    key = normalizedBudget,
                    itemCost = { candidate -> T9CandidateBudget.candidateCost(candidate.text) }
                )
            }
        }
    }

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

    private var signature: Any? = null
    private var budget = Budget(value = 0, itemCost = { 1 })
    private var budgetKey: Any = 0
    private val pages = mutableListOf<List<IndexedValue<FcitxEvent.Candidate>>>()
    private val pageEndOffsets = mutableListOf<Int>()

    var pageIndex = 0
        private set

    var candidates: List<IndexedValue<FcitxEvent.Candidate>> = emptyList()
        private set

    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()

    fun reset() {
        signature = null
        budget = Budget(value = 0, itemCost = { 1 })
        budgetKey = 0
        pages.clear()
        pageEndOffsets.clear()
        pageIndex = 0
        candidates = emptyList()
    }

    fun update(
        signature: Any,
        candidates: List<IndexedValue<FcitxEvent.Candidate>>,
        characterBudget: Int
    ) = update(signature, candidates, Budget.character(characterBudget))

    fun update(
        signature: Any,
        candidates: List<IndexedValue<FcitxEvent.Candidate>>,
        budget: Budget
    ) {
        if (this.signature == signature && budgetKey == budget.key) return
        this.signature = signature
        this.budget = budget
        this.budgetKey = budget.key
        this.candidates = candidates
        pages.clear()
        pageEndOffsets.clear()
        pageIndex = 0
    }

    fun currentPage(): Page? = pageAt(pageIndex)

    fun pageAt(index: Int): Page? {
        if (candidates.isEmpty()) return null
        val targetIndex = index.coerceAtLeast(0)
        if (!ensurePage(targetIndex)) return null
        val safeIndex = targetIndex.coerceAtMost(pages.lastIndex)
        pageIndex = safeIndex
        return pageFor(safeIndex)
    }

    fun offset(delta: Int): Page? = pageAt(pageIndex + delta)

    fun selectPageContainingOriginalIndex(originalIndex: Int): Page? {
        if (candidates.isEmpty()) return null
        var nextIndex = -1
        var index = 0
        while (ensurePage(index)) {
            if (pages[index].any { it.index == originalIndex }) {
                nextIndex = index
                break
            }
            index++
        }
        if (nextIndex < 0) {
            nextIndex = pageIndex.coerceIn(pages.indices)
        }
        pageIndex = nextIndex
        return pageFor(nextIndex)
    }

    private fun pageFor(index: Int): Page =
        Page(
            candidates = pages[index],
            index = index,
            hasPrev = index > 0,
            hasNext = pageEndOffsets[index] < candidates.size
        )

    private fun ensurePage(index: Int): Boolean {
        while (pages.size <= index) {
            if (!buildNextPage()) return false
        }
        return true
    }

    private fun buildNextPage(): Boolean {
        val start = pageEndOffsets.lastOrNull() ?: 0
        if (start >= candidates.size) return false
        var current = mutableListOf<IndexedValue<FcitxEvent.Candidate>>()
        var used = 0
        var hardUsed = 0
        var canProtectCurrentPage = true
        var offset = start
        while (offset < candidates.size) {
            val candidate = candidates[offset]
            val length = budget.itemCost(candidate.value).coerceAtLeast(1)
            val hardLength = budget.hardItemCost(candidate.value).coerceAtLeast(1)
            val canProtectCandidate = budget.canProtectItem(candidate.value)
            if (current.isNotEmpty() && used + length > budget.value) {
                val canUseProtectedSlot =
                    current.size < budget.protectedMinItems &&
                        canProtectCurrentPage &&
                        canProtectCandidate &&
                        hardUsed + hardLength <= budget.hardValue
                if (!canUseProtectedSlot) {
                    break
                }
            }
            current += candidate
            used += length
            hardUsed += hardLength
            canProtectCurrentPage = canProtectCurrentPage && canProtectCandidate
            offset++
        }
        if (current.isEmpty()) return false
        pages += current
        pageEndOffsets += offset
        return true
    }
}

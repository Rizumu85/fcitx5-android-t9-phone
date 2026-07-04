/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class ChineseT9CandidatePipeline(
    private val characterBudget: () -> Int,
    private val pageBudget: (() -> T9CandidatePager.Budget)? = null,
    private val candidateMatchesPrefix: (candidate: FcitxEvent.Candidate, prefix: String) -> Boolean
) {
    private val localBudgetPager = T9CandidatePager()
    private var localBudgetSignature: T9CandidatePagerSnapshot? = null
    private var localBudgetNoPageSignature: T9CandidatePagerSnapshot? = null
    private var filterCacheKey: FilterCacheKey? = null
    private var filterCacheResult: Pair<T9PagedCandidates, String?>? = null
    private var shownCandidateSignature = ""
    private var shownCursorContextSignature = ""
    private var hanziCursorIndex = -1

    fun reset() {
        resetLocalBudget()
        resetFilterCache()
        shownCandidateSignature = ""
        shownCursorContextSignature = ""
        hanziCursorIndex = -1
    }

    fun resetLocalBudget() {
        localBudgetPager.reset()
        localBudgetSignature = null
        localBudgetNoPageSignature = null
    }

    private fun resetFilterCache() {
        filterCacheKey = null
        filterCacheResult = null
    }

    val hasLocalBudgetCandidates: Boolean
        get() = localBudgetPager.hasCandidates

    fun offsetLocalBudgetedPage(delta: Int): Boolean {
        val current = localBudgetPager.currentPage() ?: return false
        val canOffset = if (delta > 0) current.hasNext else current.hasPrev
        if (!canOffset) return false
        return localBudgetPager.offset(delta) != null
    }

    fun filterPagedByPinyinPrefixes(
        data: FcitxEvent.PagedCandidateEvent.Data,
        prefixes: List<String>
    ): Pair<T9PagedCandidates, String?> {
        if (prefixes.isEmpty() || data.candidates.isEmpty()) {
            resetFilterCache()
            return T9PagedCandidates.passthrough(data) to null
        }
        val key = FilterCacheKey(
            candidates = buildCandidateSnapshot(data),
            prefixes = prefixes.toList()
        )
        if (key == filterCacheKey) {
            filterCacheResult?.let { return it }
        }
        val matched = matchCandidates(data.candidates.withIndex().toList(), prefixes)
        val result = if (matched.candidates.isEmpty()) {
            T9PagedCandidates(
                data = data.copy(candidates = emptyArray(), cursorIndex = -1),
                originalIndices = intArrayOf()
            ) to null
        } else {
            val pager = T9CandidatePager()
            pager.update("filtered", matched.candidates, currentBudget())
            val page = pager.currentPage()
            if (page == null) {
                T9PagedCandidates.passthrough(data) to matched.prefix
            } else if (
                page.candidates.size == data.candidates.size &&
                page.candidates.indices.all { page.candidates[it].index == it }
            ) {
                T9PagedCandidates.passthrough(data) to matched.prefix
            } else {
                page.toPagedCandidates(
                    layoutHint = data.layoutHint,
                    cursorIndex = page.cursorIndexForOriginalIndex(data.cursorIndex),
                    hasExternalNext = data.hasNext
                ) to matched.prefix
            }
        }
        filterCacheKey = key
        filterCacheResult = result
        return result
    }

    fun buildLocalBudgetedPagedFromCurrentPage(
        data: FcitxEvent.PagedCandidateEvent.Data
    ): T9PagedCandidates? {
        if (data.candidates.isEmpty()) return null
        val signature = buildCandidateSnapshot(data)
        if (signature == localBudgetNoPageSignature) return null
        if (signature != localBudgetSignature) {
            val indexedCandidates = dedupeDisplayCandidates(data.candidates.withIndex().toList())
            localBudgetPager.update(signature, indexedCandidates, currentBudget())
            localBudgetSignature = signature
        }
        val page = localBudgetPager.currentPage() ?: return null
        if (page.candidates.size == data.candidates.size && !page.hasPrev && !page.hasNext) {
            localBudgetNoPageSignature = signature
            localBudgetPager.reset()
            localBudgetSignature = null
            return null
        }
        return page.toPagedCandidates(
            layoutHint = data.layoutHint,
            cursorIndex = 0,
            hasExternalPrev = data.hasPrev,
            hasExternalNext = data.hasNext
        )
    }

    fun applyHanziCursor(
        data: FcitxEvent.PagedCandidateEvent.Data,
        cursorContextSignature: String
    ): FcitxEvent.PagedCandidateEvent.Data {
        val signature = buildShownCandidateSignature(data)
        if (signature != shownCandidateSignature ||
            cursorContextSignature != shownCursorContextSignature
        ) {
            shownCandidateSignature = signature
            shownCursorContextSignature = cursorContextSignature
            hanziCursorIndex = data.candidates.indices.firstOrNull() ?: -1
        } else if (hanziCursorIndex !in data.candidates.indices) {
            hanziCursorIndex = data.candidates.indices.firstOrNull() ?: -1
        }
        return if (data.cursorIndex == hanziCursorIndex) data else data.copy(cursorIndex = hanziCursorIndex)
    }

    fun moveHanziCursor(
        data: FcitxEvent.PagedCandidateEvent.Data,
        index: Int
    ): FcitxEvent.PagedCandidateEvent.Data? {
        if (index !in data.candidates.indices) return null
        hanziCursorIndex = index
        return if (data.cursorIndex == index) data else data.copy(cursorIndex = index)
    }

    fun buildCursorContextSignature(preedit: CharSequence, prefixes: List<String>): String =
        buildString {
            append(preedit).append('|')
            append(prefixes.joinToString(separator = "/"))
        }

    private fun matchCandidates(
        candidates: List<IndexedValue<FcitxEvent.Candidate>>,
        prefixes: List<String>
    ): T9BulkCandidateLoader.MatchResult {
        prefixes.forEach { prefix ->
            val matches = dedupeDisplayCandidates(candidates.filter {
                candidateMatchesPrefix(it.value, prefix)
            })
            if (matches.isNotEmpty()) {
                return T9BulkCandidateLoader.MatchResult(prefix, matches)
            }
        }
        return T9BulkCandidateLoader.MatchResult(null, emptyList())
    }

    private fun dedupeDisplayCandidates(
        candidates: List<IndexedValue<FcitxEvent.Candidate>>
    ): List<IndexedValue<FcitxEvent.Candidate>> {
        if (candidates.size < 2) return candidates
        val seen = HashSet<String>(candidates.size)
        return candidates.filter { (_, candidate) ->
            seen.add(candidate.text)
        }
    }

    private fun buildCandidateSnapshot(
        data: FcitxEvent.PagedCandidateEvent.Data
    ): T9CandidatePagerSnapshot =
        T9CandidateSnapshots.pagerSnapshot(data, currentBudget().key)

    private fun currentBudget(): T9CandidatePager.Budget =
        pageBudget?.invoke() ?: T9CandidatePager.Budget.character(characterBudget())

    private fun buildShownCandidateSignature(data: FcitxEvent.PagedCandidateEvent.Data): String =
        buildString {
            data.candidates.forEach {
                append(it.label).append('|').append(it.text).append('|').append(it.comment).append('\n')
            }
            append(data.hasPrev).append('|').append(data.hasNext)
        }

    private data class FilterCacheKey(
        val candidates: T9CandidatePagerSnapshot,
        val prefixes: List<String>
    )
}

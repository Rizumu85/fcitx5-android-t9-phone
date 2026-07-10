/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class ChineseT9CandidatePipeline(
    private val characterBudget: () -> Int,
    private val widthBudget: () -> T9CandidateWidthBudget?,
    private val candidateMatchesPrefix: (candidate: FcitxEvent.Candidate, prefix: String) -> Boolean
) {
    data class BulkFilterState(
        val paged: T9PagedCandidates?,
        val matchedPrefix: String?,
        val pending: Boolean
    )

    private val localBudgetPager = T9CandidatePager()
    private val bulkCandidateLoader = T9BulkCandidateLoader(
        characterBudget = characterBudget,
        widthBudget = widthBudget,
        candidateMatchesPrefix = candidateMatchesPrefix
    )
    private var localBudgetSignature = ""
    private var localBudgetNoPageSignature = ""
    private var bulkFilteredPaged: T9PagedCandidates? = null
    private var bulkFilteredMatchedPrefix: String? = null
    private var shownCandidateSignature = ""
    private var shownCursorContextSignature = ""
    private var hanziCursorIndex = -1
    private var localBudgetInputCandidates: Array<FcitxEvent.Candidate>? = null
    private var localBudgetInputKey: LocalBudgetInputKey? = null
    private var localBudgetInputSignature = ""
    private var localBudgetCachedPaged: T9PagedCandidates? = null
    private var localBudgetCachedPageIndex = -1
    private var localBudgetCachedNoPage = false

    private data class LocalBudgetInputKey(
        val cursorIndex: Int,
        val layoutHint: FcitxEvent.PagedCandidateEvent.LayoutHint,
        val hasPrev: Boolean,
        val hasNext: Boolean,
        val originalIndicesHash: Int,
        val characterBudget: Int,
        val widthSignature: String
    )

    fun reset() {
        resetBulkFilter()
        resetLocalBudget()
        shownCandidateSignature = ""
        shownCursorContextSignature = ""
        hanziCursorIndex = -1
    }

    fun resetBulkFilter() {
        bulkFilteredPaged = null
        bulkFilteredMatchedPrefix = null
        bulkCandidateLoader.reset()
    }

    fun resetLocalBudget() {
        localBudgetPager.reset()
        localBudgetSignature = ""
        localBudgetNoPageSignature = ""
        localBudgetInputCandidates = null
        localBudgetInputKey = null
        localBudgetInputSignature = ""
        localBudgetCachedPaged = null
        localBudgetCachedPageIndex = -1
        localBudgetCachedNoPage = false
    }

    val hasLocalBudgetCandidates: Boolean
        get() = localBudgetPager.hasCandidates

    val hasBulkFilteredCandidates: Boolean
        get() = bulkCandidateLoader.hasCandidates

    val bulkFilterState: BulkFilterState
        get() = BulkFilterState(
            paged = bulkFilteredPaged,
            matchedPrefix = bulkFilteredMatchedPrefix,
            pending = bulkCandidateLoader.pending
        )

    fun bulkFilterRequestSignature(
        prefixes: List<String>,
        preedit: CharSequence,
        candidates: Array<FcitxEvent.Candidate>
    ): String =
        bulkCandidateLoader.requestSignature(prefixes, preedit, candidates)

    fun shouldRequestBulkFilter(signature: String): Boolean =
        bulkCandidateLoader.shouldRequest(signature)

    fun startBulkFilterRequest(prefixes: List<String>, signature: String) {
        bulkCandidateLoader.startRequest(prefixes, signature)
        bulkFilteredPaged = null
        bulkFilteredMatchedPrefix = null
    }

    fun finishBulkFilterRequest(
        signature: String,
        rawCandidates: List<String>,
        prefixes: List<String>,
        layoutHint: FcitxEvent.PagedCandidateEvent.LayoutHint
    ): BulkFilterState? {
        val result = bulkCandidateLoader.finishRequest(signature, rawCandidates, prefixes)
            ?: return null
        bulkFilteredMatchedPrefix = result.matchedPrefix
        bulkFilteredPaged = result.page
            ?.takeUnless { it.candidates.isEmpty() }
            ?.toPagedCandidates(
                layoutHint = layoutHint,
                cursorIndex = 0
            )
        return bulkFilterState
    }

    fun offsetBulkFilteredPage(
        delta: Int,
        layoutHint: FcitxEvent.PagedCandidateEvent.LayoutHint
    ): Boolean {
        val page = bulkCandidateLoader.offset(delta) ?: return false
        bulkFilteredPaged = page.takeUnless { it.candidates.isEmpty() }?.toPagedCandidates(
            layoutHint = layoutHint,
            cursorIndex = 0
        )
        return true
    }

    fun moveBulkFilteredCursor(index: Int): T9PagedCandidates? {
        val shown = bulkFilteredPaged ?: return null
        if (index !in shown.data.candidates.indices) return null
        // Bulk-filtered Chinese candidates are rebuilt on every UI refresh, so the cursor must
        // live with the filtered page instead of only the transient rendered snapshot.
        hanziCursorIndex = index
        bulkFilteredPaged = T9PagedCandidates(
            data = shown.data.copy(cursorIndex = index),
            originalIndices = shown.originalIndices
        )
        return bulkFilteredPaged
    }

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
            return T9PagedCandidates.passthrough(data) to null
        }
        val matched = matchCandidates(data.candidates.withIndex().toList(), prefixes)
        if (matched.candidates.isEmpty()) {
            return T9PagedCandidates(
                data = data.copy(candidates = emptyArray(), cursorIndex = -1),
                originalIndices = intArrayOf()
            ) to null
        }
        val pager = T9CandidatePager()
        pager.update("filtered", matched.candidates, characterBudget(), widthBudget())
        val page = pager.currentPage() ?: return T9PagedCandidates.passthrough(data) to matched.prefix
        if (page.candidates.size == data.candidates.size &&
            page.candidates.indices.all { page.candidates[it].index == it }
        ) {
            return T9PagedCandidates.passthrough(data) to matched.prefix
        }
        return page.toPagedCandidates(
            layoutHint = data.layoutHint,
            cursorIndex = page.cursorIndexForOriginalIndex(data.cursorIndex),
            hasExternalNext = data.hasNext
        ) to matched.prefix
    }

    fun buildLocalBudgetedPagedFromCurrentPage(
        source: T9PagedCandidates
    ): T9PagedCandidates? {
        val data = source.data
        if (data.candidates.isEmpty()) return null
        val budget = characterBudget()
        val widthBudget = widthBudget()
        val inputKey = LocalBudgetInputKey(
            cursorIndex = data.cursorIndex,
            layoutHint = data.layoutHint,
            hasPrev = data.hasPrev,
            hasNext = data.hasNext,
            originalIndicesHash = source.originalIndices.contentHashCode(),
            characterBudget = budget,
            widthSignature = widthBudget?.signature.orEmpty()
        )
        val sameInput = data.candidates === localBudgetInputCandidates &&
            inputKey == localBudgetInputKey
        if (sameInput) {
            if (localBudgetCachedNoPage) return null
            localBudgetCachedPaged?.takeIf {
                localBudgetCachedPageIndex == localBudgetPager.pageIndex
            }?.let { return it }
        }
        val signature = if (sameInput) {
            localBudgetInputSignature
        } else {
            buildCandidateSignature(data, budget, widthBudget).also {
                localBudgetInputCandidates = data.candidates
                localBudgetInputKey = inputKey
                localBudgetInputSignature = it
                localBudgetCachedPaged = null
                localBudgetCachedPageIndex = -1
                localBudgetCachedNoPage = false
            }
        }
        if (signature == localBudgetNoPageSignature) return null
        if (signature != localBudgetSignature) {
            val indexedCandidates = dedupeDisplayCandidates(source.indexedCandidates())
            localBudgetPager.update(signature, indexedCandidates, budget, widthBudget)
            localBudgetSignature = signature
        }
        val page = localBudgetPager.currentPage() ?: return null
        if (page.candidates.size == data.candidates.size && !page.hasPrev && !page.hasNext) {
            localBudgetNoPageSignature = signature
            localBudgetPager.reset()
            localBudgetSignature = ""
            localBudgetCachedPaged = null
            localBudgetCachedPageIndex = -1
            localBudgetCachedNoPage = true
            return null
        }
        return page.toPagedCandidates(
            layoutHint = data.layoutHint,
            cursorIndex = 0,
            hasExternalPrev = data.hasPrev,
            hasExternalNext = data.hasNext
        ).also {
            // Decision: a single Rime candidate update can trigger multiple layout refreshes before
            // the user presses another key. Keep the already-budgeted page by input identity so UI
            // refreshes do not rebuild a large candidate signature and page list in the hot path.
            localBudgetCachedPaged = it
            localBudgetCachedPageIndex = localBudgetPager.pageIndex
            localBudgetCachedNoPage = false
        }
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

    private fun buildCandidateSignature(
        data: FcitxEvent.PagedCandidateEvent.Data,
        characterBudget: Int,
        widthBudget: T9CandidateWidthBudget?
    ): String =
        T9CandidateSnapshots.pagerContent(data, characterBudget, widthBudget)

    private fun buildShownCandidateSignature(data: FcitxEvent.PagedCandidateEvent.Data): String =
        buildString {
            data.candidates.forEach {
                append(it.label).append('|').append(it.text).append('|').append(it.comment).append('\n')
            }
            append(data.hasPrev).append('|').append(data.hasNext)
        }
}

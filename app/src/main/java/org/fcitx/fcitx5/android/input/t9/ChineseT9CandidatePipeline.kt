/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class ChineseT9CandidatePipeline(
    private val characterBudget: () -> Int,
    private val candidateMatchesPrefix: (candidate: FcitxEvent.Candidate, prefix: String) -> Boolean
) {
    private val localBudgetPager = T9CandidatePager()
    private var localBudgetSignature = ""
    private var localBudgetNoPageSignature = ""
    private var shownCandidateSignature = ""
    private var shownCursorContextSignature = ""
    private var hanziCursorIndex = -1

    fun reset() {
        resetLocalBudget()
        shownCandidateSignature = ""
        shownCursorContextSignature = ""
        hanziCursorIndex = -1
    }

    fun resetLocalBudget() {
        localBudgetPager.reset()
        localBudgetSignature = ""
        localBudgetNoPageSignature = ""
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
    ): Pair<FcitxEvent.PagedCandidateEvent.Data, String?> {
        if (prefixes.isEmpty() || data.candidates.isEmpty()) return data to null
        val matched = matchCandidates(data.candidates.withIndex().toList(), prefixes)
        if (matched.candidates.isEmpty()) {
            return data.copy(candidates = emptyArray(), cursorIndex = -1) to null
        }
        val pager = T9CandidatePager()
        pager.update("filtered", matched.candidates, characterBudget())
        val page = pager.currentPage() ?: return data to matched.prefix
        if (page.candidates.size == data.candidates.size &&
            page.candidates.indices.all { page.candidates[it].index == it }
        ) {
            return data to matched.prefix
        }
        val filteredCandidates = page.candidates.map { it.value }
        val originallyHighlighted = data.candidates.getOrNull(data.cursorIndex)
        val newCursor = originallyHighlighted
            ?.let { filteredCandidates.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?: 0
        return data.copy(
            candidates = filteredCandidates.toTypedArray(),
            cursorIndex = newCursor,
            hasPrev = page.hasPrev,
            hasNext = page.hasNext || data.hasNext
        ) to matched.prefix
    }

    fun buildLocalBudgetedPagedFromCurrentPage(
        data: FcitxEvent.PagedCandidateEvent.Data
    ): FcitxEvent.PagedCandidateEvent.Data? {
        if (data.candidates.isEmpty()) return null
        val signature = buildCandidateSignature(data)
        if (signature == localBudgetNoPageSignature) return null
        if (signature != localBudgetSignature) {
            val indexedCandidates = dedupeDisplayCandidates(data.candidates.withIndex().toList())
            localBudgetPager.update(signature, indexedCandidates, characterBudget())
            localBudgetSignature = signature
        }
        val page = localBudgetPager.currentPage() ?: return null
        if (page.candidates.size == data.candidates.size && !page.hasPrev && !page.hasNext) {
            localBudgetNoPageSignature = signature
            localBudgetPager.reset()
            localBudgetSignature = ""
            return null
        }
        return FcitxEvent.PagedCandidateEvent.Data(
            candidates = page.candidates.map { it.value }.toTypedArray(),
            cursorIndex = 0,
            layoutHint = data.layoutHint,
            hasPrev = data.hasPrev || page.hasPrev,
            hasNext = data.hasNext || page.hasNext
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

    fun buildOriginalIndicesForPaged(
        shown: FcitxEvent.PagedCandidateEvent.Data,
        rawPaged: FcitxEvent.PagedCandidateEvent.Data
    ): IntArray {
        if (shown.candidates.isEmpty()) return intArrayOf()
        if (shown.candidates.contentEquals(rawPaged.candidates)) {
            return IntArray(shown.candidates.size) { it }
        }
        val seenByCandidate = mutableMapOf<FcitxEvent.Candidate, Int>()
        return IntArray(shown.candidates.size) { shownIndex ->
            val target = shown.candidates[shownIndex]
            val targetSeen = seenByCandidate.getOrDefault(target, 0)
            seenByCandidate[target] = targetSeen + 1
            var seen = 0
            rawPaged.candidates.forEachIndexed { rawIndex, rawCandidate ->
                if (rawCandidate == target) {
                    if (seen == targetSeen) return@IntArray rawIndex
                    seen += 1
                }
            }
            -1
        }
    }

    fun originalCandidateIndexForShown(
        shown: FcitxEvent.PagedCandidateEvent.Data,
        shownIndex: Int,
        rawPaged: FcitxEvent.PagedCandidateEvent.Data,
        shownOriginalIndices: IntArray
    ): Int? {
        shownOriginalIndices.getOrNull(shownIndex)?.takeIf { it >= 0 }?.let { return it }
        val target = shown.candidates.getOrNull(shownIndex) ?: return null
        if (shown.candidates.contentEquals(rawPaged.candidates)) return shownIndex
        val sameCandidateBeforeTarget = shown.candidates
            .take(shownIndex)
            .count { it == target }
        var seen = 0
        rawPaged.candidates.forEachIndexed { rawIndex, rawCandidate ->
            if (rawCandidate == target) {
                if (seen == sameCandidateBeforeTarget) return rawIndex
                seen += 1
            }
        }
        return null
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

    private fun buildCandidateSignature(data: FcitxEvent.PagedCandidateEvent.Data): String =
        buildString {
            append(characterBudget()).append('|')
            data.candidates.forEach {
                append(it.label).append('|').append(it.text).append('|').append(it.comment).append('\n')
            }
        }

    private fun buildShownCandidateSignature(data: FcitxEvent.PagedCandidateEvent.Data): String =
        buildString {
            data.candidates.forEach {
                append(it.label).append('|').append(it.text).append('|').append(it.comment).append('\n')
            }
            append(data.hasPrev).append('|').append(data.hasNext)
        }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9BulkCandidateLoader(
    private val characterBudget: () -> Int,
    private val widthBudget: () -> T9CandidateWidthBudget?,
    private val candidateMatchesPrefix: (candidate: FcitxEvent.Candidate, prefix: String) -> Boolean
) {

    data class MatchResult(
        val prefix: String?,
        val candidates: List<IndexedValue<FcitxEvent.Candidate>>
    )

    data class PageResult(
        val matchedPrefix: String?,
        val page: T9CandidatePager.Page?
    )

    private val pager = T9CandidatePager()
    private var requestSignature = ""
    private var prefixSignature = ""

    var pending = false
        private set

    val hasCandidates: Boolean
        get() = pager.hasCandidates

    fun reset() {
        requestSignature = ""
        prefixSignature = ""
        pending = false
        pager.reset()
    }

    fun requestSignature(
        prefixes: List<String>,
        preedit: CharSequence,
        candidates: Array<FcitxEvent.Candidate>
    ): String {
        val prefixSignature = prefixes.joinToString(separator = "/")
        return buildString {
            append(prefixSignature).append('|')
            append(characterBudget()).append('|')
            append(widthBudget()?.signature.orEmpty()).append('|')
            append(preedit).append('|')
            append(candidates.contentHashCode())
        }
    }

    fun shouldRequest(signature: String): Boolean = signature != requestSignature

    fun startRequest(prefixes: List<String>, signature: String): Boolean {
        val nextPrefixSignature = prefixes.joinToString(separator = "/")
        val prefixChanged = nextPrefixSignature != prefixSignature
        prefixSignature = nextPrefixSignature
        requestSignature = signature
        pending = true
        pager.reset()
        return prefixChanged
    }

    fun finishRequest(
        signature: String,
        rawCandidates: List<String>,
        prefixes: List<String>
    ): PageResult? {
        if (signature != requestSignature) return null
        val parsedCandidates = rawCandidates.mapIndexedNotNull { index, raw ->
            parseCandidate(raw)?.let { IndexedValue(index, it) }
        }
        val match = if (prefixes.isEmpty()) {
            MatchResult(null, dedupeDisplayCandidates(parsedCandidates))
        } else {
            matchCandidates(parsedCandidates, prefixes)
        }
        pending = false
        pager.update(signature, match.candidates, characterBudget(), widthBudget())
        return PageResult(match.prefix, pager.currentPage())
    }

    fun offset(delta: Int): T9CandidatePager.Page? = pager.offset(delta)

    fun parseCandidate(raw: String): FcitxEvent.Candidate? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val splitAt = trimmed.indexOf(' ')
        return if (splitAt <= 0 || splitAt == trimmed.lastIndex) {
            FcitxEvent.Candidate(label = "", text = trimmed, comment = "")
        } else {
            FcitxEvent.Candidate(
                label = "",
                text = trimmed.substring(0, splitAt),
                comment = trimmed.substring(splitAt + 1).trim()
            )
        }
    }

    fun dedupeDisplayCandidates(
        candidates: List<IndexedValue<FcitxEvent.Candidate>>
    ): List<IndexedValue<FcitxEvent.Candidate>> {
        if (candidates.size < 2) return candidates
        val seen = HashSet<String>(candidates.size)
        return candidates.filter { (_, candidate) ->
            seen.add(candidate.text)
        }
    }

    fun matchCandidates(
        candidates: List<IndexedValue<FcitxEvent.Candidate>>,
        prefixes: List<String>
    ): MatchResult {
        prefixes.forEach { prefix ->
            val matches = dedupeDisplayCandidates(candidates.filter {
                candidateMatchesPrefix(it.value, prefix)
            })
            if (matches.isNotEmpty()) {
                return MatchResult(prefix, matches)
            }
        }
        return MatchResult(null, emptyList())
    }
}

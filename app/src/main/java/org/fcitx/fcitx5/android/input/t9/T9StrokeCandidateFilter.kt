/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9StrokeCandidateFilter(
    private val isRenderable: (FcitxEvent.Candidate) -> Boolean
) {
    private var sourceCandidates: Array<FcitxEvent.Candidate>? = null
    private var filteredCandidates: Array<FcitxEvent.Candidate> = emptyArray()
    private var originalIndices: IntArray = intArrayOf()
    private val renderability = object : LinkedHashMap<String, Boolean>(
        MaxCachedCandidates,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > MaxCachedCandidates
    }

    fun filter(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates {
        if (sourceCandidates !== data.candidates) rebuild(data.candidates)
        if (originalIndices.size == data.candidates.size) return T9PagedCandidates.passthrough(data)
        val cursorIndex = originalIndices.indexOf(data.cursorIndex)
            .takeIf { it >= 0 }
            ?: filteredCandidates.indices.firstOrNull()
            ?: -1
        return T9PagedCandidates(
            data = data.copy(
                candidates = filteredCandidates,
                cursorIndex = cursorIndex
            ),
            originalIndices = originalIndices
        )
    }

    fun clearSource() {
        sourceCandidates = null
        filteredCandidates = emptyArray()
        originalIndices = intArrayOf()
    }

    private fun rebuild(candidates: Array<FcitxEvent.Candidate>) {
        sourceCandidates = candidates
        val kept = candidates.withIndex().filter { (_, candidate) ->
            // Stroke keeps the full Han dictionary, so device font coverage must be decided at
            // the presentation boundary rather than deleting rare characters for every user.
            candidate.text.isNotBlank() && renderability.getOrPut(candidate.text) {
                isRenderable(candidate)
            }
        }
        filteredCandidates = kept.map { it.value }.toTypedArray()
        originalIndices = kept.map { it.index }.toIntArray()
    }

    companion object {
        private const val MaxCachedCandidates = 256
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class T9FilteredCandidateScan(
    val batchSize: Int = 80,
    private val matchLimit: Int = 80,
    private val matchingText: (String) -> String?
) {
    init {
        require(batchSize > 0 && matchLimit > 0)
    }

    private val seen = HashSet<String>()
    private val matches = ArrayList<IndexedValue<String>>()

    var nextOffset = 0
        private set
    var complete = false
        private set
    val candidates: List<IndexedValue<String>>
        get() = matches.toList()

    fun append(batch: List<String>) {
        check(!complete)
        batch.forEachIndexed { index, raw ->
            val text = matchingText(raw) ?: return@forEachIndexed
            if (matches.size < matchLimit && seen.add(text)) {
                matches += IndexedValue(nextOffset + index, raw)
            }
        }
        nextOffset += batch.size
        // The budget limits matching results, never the unfiltered source. Otherwise a valid
        // reading can look empty simply because unrelated readings occupy the first batch.
        complete = batch.size < batchSize || matches.size >= matchLimit
    }
}

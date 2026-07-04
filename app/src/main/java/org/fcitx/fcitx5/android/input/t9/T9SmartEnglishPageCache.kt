/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9SmartEnglishPageCache(
    private val characterBudget: () -> Int
) {
    private val pager = T9CandidatePager()
    private var contentSignature = ""
    private var cachedCursorIndex = Int.MIN_VALUE
    private var cachedPaged: T9PagedCandidates? = null

    val hasCandidates: Boolean
        get() = pager.hasCandidates

    fun reset() {
        pager.reset()
        contentSignature = ""
        cachedCursorIndex = Int.MIN_VALUE
        cachedPaged = null
    }

    fun build(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates {
        val budget = characterBudget()
        val signature = T9CandidateSnapshots.pagerContent(data, budget)
        val selectedIndex = data.candidates.indices
            .takeIf { !it.isEmpty() }
            ?.let { data.cursorIndex.coerceIn(it) }
            ?: -1
        cachedPaged?.let { cached ->
            if (signature == contentSignature && selectedIndex == cachedCursorIndex) {
                return cached
            }
        }
        if (signature != contentSignature) {
            pager.update(signature, data.candidates.withIndex().toList(), budget)
            contentSignature = signature
        }
        cachedCursorIndex = selectedIndex
        val next = pager.selectPageContainingOriginalIndex(selectedIndex)
            ?.let { page ->
                page.toPagedCandidates(
                layoutHint = data.layoutHint,
                    cursorIndex = page.cursorIndexForOriginalIndex(selectedIndex)
                )
            }
            ?: T9PagedCandidates.passthrough(data)
        cachedPaged = next
        return next
    }

    fun offset(delta: Int): T9CandidatePager.Page? {
        val page = pager.offset(delta)
        cachedPaged = null
        cachedCursorIndex = Int.MIN_VALUE
        return page
    }
}

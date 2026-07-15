/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

class HandwritingCandidateSession(
    private val requestedPageSize: () -> Int
) {
    private var candidates = emptyList<String>()
    private var selectedIndex = 0

    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()

    val selectedOriginalIndex: Int?
        get() = selectedIndex.takeIf { candidates.isNotEmpty() }

    fun replace(candidates: List<String>) {
        this.candidates = candidates
        selectedIndex = 0
    }

    fun clear() {
        candidates = emptyList()
        selectedIndex = 0
    }

    fun snapshot(): HandwritingCandidatePage {
        if (candidates.isEmpty()) return HandwritingCandidatePage.Empty
        val pageSize = pageSize()
        val pageIndex = selectedIndex / pageSize
        val pageStart = pageIndex * pageSize
        val pageEnd = minOf(pageStart + pageSize, candidates.size)
        return HandwritingCandidatePage(
            items = (pageStart until pageEnd).map { originalIndex ->
                HandwritingCandidateItem(
                    originalIndex = originalIndex,
                    text = candidates[originalIndex],
                    shortcutLabel = ShortcutLabels[originalIndex - pageStart]
                )
            },
            selectedOriginalIndex = selectedIndex,
            pageIndex = pageIndex,
            pageCount = (candidates.size + pageSize - 1) / pageSize
        )
    }

    fun candidateAt(originalIndex: Int): String? = candidates.getOrNull(originalIndex)

    fun move(delta: Int): Boolean {
        if (candidates.isEmpty() || delta == 0) return false
        val next = (selectedIndex + delta).coerceIn(candidates.indices)
        if (next == selectedIndex) return false
        selectedIndex = next
        return true
    }

    fun offsetPage(delta: Int): Boolean {
        if (candidates.isEmpty() || delta == 0) return false
        val pageSize = pageSize()
        val pageCount = (candidates.size + pageSize - 1) / pageSize
        val currentPage = selectedIndex / pageSize
        val nextPage = (currentPage + delta).coerceIn(0, pageCount - 1)
        if (nextPage == currentPage) return false
        selectedIndex = nextPage * pageSize
        return true
    }

    fun originalIndexForShortcut(shortcutIndex: Int): Int? {
        if (candidates.isEmpty() || shortcutIndex !in ShortcutLabels.indices) return null
        val pageSize = pageSize()
        val pageStart = selectedIndex / pageSize * pageSize
        return (pageStart + shortcutIndex).takeIf { it in candidates.indices }
    }

    private fun pageSize(): Int {
        // A phone keypad exposes exactly ten shortcut keys. Capping the local handwriting page
        // keeps every visible shortcut actionable even when the general candidate budget is larger.
        return requestedPageSize().coerceIn(1, ShortcutLabels.size)
    }

    private companion object {
        val ShortcutLabels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    }
}

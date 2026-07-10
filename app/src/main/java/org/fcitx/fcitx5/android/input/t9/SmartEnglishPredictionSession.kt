/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class SmartEnglishPredictionSession(
    private val predictionProvider: (previousWords: List<String>, limit: Int) -> List<String>,
    private val candidateLimit: Int
) {
    private var contextWords: List<String> = emptyList()
    private var candidates: List<String> = emptyList()
    private var cursorIndex = 0
    private var hidden = false

    val isVisible: Boolean
        get() = !hidden && candidates.isNotEmpty()

    val cursor: Int
        get() = cursorIndex

    val hasContext: Boolean
        get() = contextWords.isNotEmpty()

    fun reset() {
        contextWords = emptyList()
        candidates = emptyList()
        cursorIndex = 0
        hidden = false
    }

    fun updateContext(words: List<String>) {
        val normalized = words.mapNotNull(SmartEnglishPredictionDictionary::normalizePredictionWord)
        contextWords = normalized.takeLast(ContextWindowSize)
        hidden = false
        rebuildCandidates()
    }

    fun hide(): Boolean {
        if (!isVisible) return false
        hidden = true
        cursorIndex = 0
        return true
    }

    fun showAgain(): Boolean {
        if (!hidden || candidates.isEmpty()) return false
        hidden = false
        cursorIndex = 0
        return true
    }

    fun rawCandidates(): List<String> =
        if (isVisible) candidates else emptyList()

    fun moveCandidate(delta: Int): Boolean {
        if (!isVisible) return false
        val next = (cursorIndex + delta).coerceIn(0, candidates.lastIndex)
        if (next == cursorIndex) return false
        cursorIndex = next
        return true
    }

    fun setCandidateIndex(index: Int): Boolean {
        if (!isVisible || index !in candidates.indices) return false
        cursorIndex = index
        return true
    }

    fun selectedCandidate(index: Int? = null): String? =
        rawCandidates().getOrNull(index ?: cursorIndex)

    fun refreshCandidates() {
        if (contextWords.isNotEmpty()) rebuildCandidates()
    }

    private fun rebuildCandidates() {
        candidates = if (contextWords.isEmpty()) {
            emptyList()
        } else {
            predictionProvider(contextWords, candidateLimit)
        }
        cursorIndex = 0
    }

    companion object {
        private const val ContextWindowSize = 3
    }
}

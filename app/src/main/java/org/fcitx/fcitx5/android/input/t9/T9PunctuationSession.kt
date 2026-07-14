/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9PunctuationSession(
    private val chinesePunctuation: List<String> = listOf(
        "，", "。", "？", "！", "、", "：", "；", "…", "——", "“", "”", "‘", "’",
        "（", "）", "《", "》", "〈", "〉", "【", "】", "「", "」", "『", "』", "·",
        "～", "￥", "％", "＋", "－", "×", "÷", "＝", "℃"
    ),
    private val englishPunctuation: List<String> = listOf(
        ",", ".", "?", "!", "'", "\"", "-", "@", "/", ":", ";", "(", ")", "[", "]",
        "{", "}", "<", ">", "_", "+", "=", "*", "&", "#", "%", "$", "~", "`", "\\",
        "|", "^"
    ),
    includeNewline: Boolean = false
) {
    companion object {
        const val NewlineSymbol = "↵"
    }

    enum class Set {
        CHINESE,
        ENGLISH
    }

    private data class State(
        val set: Set = Set.CHINESE,
        val index: Int = 0,
        val entry: Entry? = null
    ) {
        val isPending: Boolean
            get() = entry != null
    }

    private data class Entry(
        val displayText: String,
        val commitText: String = displayText
    )

    private val newlineEntry = includeNewline.takeIf { it }
        ?.let { Entry(displayText = NewlineSymbol, commitText = "\n") }

    private var state = State()

    val isPending: Boolean
        get() = state.isPending

    val pendingText: String?
        get() = state.entry?.displayText

    fun showChineseCandidates(): String {
        state = State(set = Set.CHINESE)
        return showPending()
    }

    fun showEnglishCandidates(): String {
        state = State(set = Set.ENGLISH)
        return showPending()
    }

    fun paged(): FcitxEvent.PagedCandidateEvent.Data? {
        if (!state.isPending) return null
        val punctuations = activePunctuationList()
        return FcitxEvent.PagedCandidateEvent.Data(
            candidates = punctuations.map {
                FcitxEvent.Candidate(label = "", text = it.displayText, comment = "")
            }.toTypedArray(),
            cursorIndex = state.index.coerceIn(punctuations.indices),
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
    }

    fun selectCandidate(index: Int): String? {
        val punctuations = activePunctuationList()
        if (!state.isPending || index !in punctuations.indices) return null
        val entry = punctuations[index]
        state = state.copy(index = index, entry = entry)
        return entry.displayText
    }

    fun toggleSet(): String? {
        if (!state.isPending) return null
        val nextSet = when (state.set) {
            Set.CHINESE -> Set.ENGLISH
            Set.ENGLISH -> Set.CHINESE
        }
        val nextList = punctuationList(nextSet)
        state = state.copy(
            set = nextSet,
            index = state.index.coerceIn(nextList.indices)
        )
        return showPending()
    }

    fun commit(): String? {
        val punctuation = state.entry?.commitText ?: return null
        state = State()
        return punctuation
    }

    fun cancel(): Boolean {
        if (!state.isPending) return false
        state = State()
        return true
    }

    private fun showPending(): String {
        val punctuations = activePunctuationList()
        val index = state.index.coerceIn(punctuations.indices)
        val punctuation = punctuations[index]
        state = state.copy(index = index, entry = punctuation)
        return punctuation.displayText
    }

    private fun activePunctuationList(): List<Entry> = punctuationList(state.set)

    private fun punctuationList(set: Set): List<Entry> {
        val punctuation = when (set) {
        Set.CHINESE -> chinesePunctuation
        Set.ENGLISH -> englishPunctuation
        }.map(::Entry)
        // The pager pins this semantic action to the first page tail without changing punctuation
        // ordering or making a word-like label wider than the universal return symbol.
        return newlineEntry?.let { punctuation + it } ?: punctuation
    }
}

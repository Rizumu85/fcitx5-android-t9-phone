/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

data class ChineseT9InputSnapshot(
    val rawSequence: String,
    val digitSequence: String,
    val currentSegment: String,
    val fullComposition: String,
    val model: T9CompositionModel,
    val keyCount: Int,
    val filterPrefixes: List<String>,
    val hasPendingPinyinSelection: Boolean,
    val sessionRevision: Long,
    val scheme: ChineseT9Scheme = ChineseT9Scheme.PINYIN,
    val selectedReading: String? = null,
    val hasInvalidReading: Boolean = false
) {
    fun compositionTicket(): ChineseT9CompositionTicket =
        ChineseT9CompositionTicket(
            scheme = scheme,
            rawSequence = rawSequence,
            digitSequence = digitSequence,
            sessionRevision = sessionRevision
        )

    fun presentationKey(
        pendingPunctuationText: String?,
        inputPreedit: String,
        candidateComment: String,
        candidateCursorIndex: Int
    ): ChineseT9PresentationSnapshotKey =
        ChineseT9PresentationSnapshotKey(
            pendingPunctuationText = pendingPunctuationText,
            rawSequence = rawSequence,
            digitSequence = digitSequence,
            currentSegment = currentSegment,
            fullComposition = fullComposition,
            model = model,
            inputPreedit = inputPreedit,
            candidateComment = candidateComment,
            candidateCursorIndex = candidateCursorIndex,
            sessionRevision = sessionRevision,
            scheme = scheme,
            selectedReading = selectedReading
        )

    fun presentationKey(
        pendingPunctuationText: String?,
        inputPanel: FcitxEvent.InputPanelEvent.Data,
        paged: FcitxEvent.PagedCandidateEvent.Data
    ): ChineseT9PresentationSnapshotKey {
        val candidateComment = paged.candidates.getOrNull(paged.cursorIndex)?.comment
            ?: paged.candidates.firstOrNull()?.comment.orEmpty()
        return presentationKey(
            pendingPunctuationText = pendingPunctuationText,
            inputPreedit = inputPanel.preedit.toString(),
            candidateComment = candidateComment,
            candidateCursorIndex = paged.cursorIndex
        )
    }
}

data class ChineseT9CompositionTicket(
    val scheme: ChineseT9Scheme,
    val rawSequence: String,
    val digitSequence: String,
    val sessionRevision: Long
)

data class ChineseT9PresentationSnapshotKey(
    val pendingPunctuationText: String?,
    val rawSequence: String,
    val digitSequence: String,
    val currentSegment: String,
    val fullComposition: String,
    val model: T9CompositionModel,
    val inputPreedit: String,
    val candidateComment: String,
    val candidateCursorIndex: Int,
    val sessionRevision: Long,
    val scheme: ChineseT9Scheme = ChineseT9Scheme.PINYIN,
    val selectedReading: String? = null
)

class ChineseT9PresentationSnapshotCache {
    private val snapshots = object : LinkedHashMap<ChineseT9PresentationSnapshotKey, T9PresentationState>(
        MaxSnapshots,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<ChineseT9PresentationSnapshotKey, T9PresentationState>?
        ): Boolean = size > MaxSnapshots
    }

    fun getOrBuild(
        key: ChineseT9PresentationSnapshotKey,
        build: () -> T9PresentationState
    ): T9PresentationState {
        snapshots[key]?.let { return it }
        return build().also { state ->
            snapshots[key] = state
        }
    }

    fun reset() {
        snapshots.clear()
    }

    companion object {
        private const val MaxSnapshots = 16
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

data class ChineseT9PresentationSnapshotKey(
    val pendingPunctuationText: String?,
    val inputPreedit: String,
    val candidateComment: String,
    val candidateCursorIndex: Int,
    val rawSequence: String,
    val digitSequence: String,
    val currentSegment: String,
    val fullComposition: String,
    val model: T9CompositionModel
)

data class ChineseT9PresentationSnapshot(
    val key: ChineseT9PresentationSnapshotKey,
    val state: T9PresentationState
)

class ChineseT9PresentationSnapshotCache {
    private var snapshot: ChineseT9PresentationSnapshot? = null

    fun getOrBuild(
        key: ChineseT9PresentationSnapshotKey,
        build: () -> T9PresentationState
    ): T9PresentationState {
        snapshot?.takeIf { it.key == key }?.let { return it.state }
        return build().also { state ->
            snapshot = ChineseT9PresentationSnapshot(key, state)
        }
    }

    fun reset() {
        snapshot = null
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

data class HandwritingPoint(
    val x: Float,
    val y: Float,
    val timeMillis: Long,
    val pressure: Float = 1f
)

data class HandwritingStroke(val points: List<HandwritingPoint>)

data class HandwritingRecognition(
    val text: String,
    val score: Float
)

enum class HandwritingModelState {
    PREPARING_OFFLINE,
    DOWNLOADING_ENHANCED,
    OFFLINE_READY,
    ENHANCED_READY,
    ENHANCED_DOWNLOAD_FAILED
}

data class HandwritingViewState(
    val strokes: List<HandwritingStroke>,
    val modelState: HandwritingModelState,
    val recognizing: Boolean,
    val noMatch: Boolean
)

data class HandwritingUiSnapshot(
    val revision: Long,
    val candidates: List<String>,
    val selectedIndex: Int
) {
    val contentKey: String
        // Selection revisions must not invalidate candidate pagination. The candidate sequence is
        // the complete paging input; keeping this key independent lets focus moves reuse pages.
        get() = candidates.joinToString("\u0001")
}

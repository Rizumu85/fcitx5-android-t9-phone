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
    OFFLINE_READY,
    ENHANCED_MODEL_MISSING,
    ENHANCED_READY
}

data class HandwritingViewState(
    val strokes: List<HandwritingStroke>,
    val candidatePage: HandwritingCandidatePage,
    val modelState: HandwritingModelState,
    val recognizing: Boolean,
    val noMatch: Boolean,
    val pronunciation: HandwritingPronunciationFeedback?
)

data class HandwritingCandidateItem(
    val originalIndex: Int,
    val text: String,
    val shortcutLabel: String
)

data class HandwritingCandidatePage(
    val items: List<HandwritingCandidateItem>,
    val selectedOriginalIndex: Int,
    val pageIndex: Int,
    val pageCount: Int
) {
    val hasPreviousPage: Boolean
        get() = pageIndex > 0

    val hasNextPage: Boolean
        get() = pageIndex + 1 < pageCount

    companion object {
        val Empty = HandwritingCandidatePage(
            items = emptyList(),
            selectedOriginalIndex = 0,
            pageIndex = 0,
            pageCount = 0
        )
    }
}

data class HandwritingPronunciationFeedback(
    val character: String,
    val readings: List<String>
) {
    companion object {
        fun create(character: String, readings: List<String>): HandwritingPronunciationFeedback? {
            val normalized = readings
                .asSequence()
                .map { it.trim() }
                .filter(String::isNotEmpty)
                .distinct()
                .toList()
            return if (character.isBlank() || normalized.isEmpty()) {
                null
            } else {
                HandwritingPronunciationFeedback(character, normalized)
            }
        }
    }
}

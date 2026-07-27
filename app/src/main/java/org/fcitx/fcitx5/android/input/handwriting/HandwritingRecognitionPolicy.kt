/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

internal enum class HandwritingRecognitionBackend {
    OFFLINE,
    ENHANCED
}

internal object HandwritingRecognitionPolicy {
    fun selectBackend(
        language: HandwritingLanguage,
        enhancedReady: Boolean
    ): HandwritingRecognitionBackend = when {
        language == HandwritingLanguage.ENGLISH -> HandwritingRecognitionBackend.ENHANCED
        enhancedReady -> HandwritingRecognitionBackend.ENHANCED
        else -> HandwritingRecognitionBackend.OFFLINE
    }

    fun mergeChineseCandidates(
        enhanced: List<HandwritingRecognition>,
        offline: List<HandwritingRecognition>,
        limit: Int
    ): List<HandwritingRecognition> {
        if (limit <= 0) return emptyList()
        val seen = HashSet<String>(limit)
        return sequenceOf(enhanced, offline)
            .flatten()
            .filter { seen.add(it.text) }
            .take(limit)
            .toList()
    }
}

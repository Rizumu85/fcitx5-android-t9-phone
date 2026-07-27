/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.fcitx.fcitx5.android.input.t9.ChineseT9OutputScript

internal class ChineseHandwritingSession(
    private val predictionProvider: ChineseHandwritingPredictionProvider,
    private val predictionEnabled: () -> Boolean,
    private val outputScript: () -> ChineseT9OutputScript,
    private val candidateLimit: Int
) {
    data class PredictionRequest(
        val context: String,
        val script: ChineseT9OutputScript,
        val limit: Int
    )

    private val preContext = HandwritingPreContext()

    fun begin(editorPreContext: String) {
        preContext.begin(editorPreContext)
    }

    fun clear() {
        preContext.clear()
    }

    fun recognitionPreContext(): String = preContext.snapshot()

    fun commitLiteral(text: String) {
        preContext.append(text)
    }

    fun commitCandidate(text: String, continuePrediction: Boolean): PredictionRequest? {
        preContext.append(text)
        return if (predictionEnabled() && continuePrediction && text.isNotBlank()) {
            PredictionRequest(text, outputScript(), candidateLimit)
        } else {
            null
        }
    }

    suspend fun resolve(request: PredictionRequest): List<String> =
        predictionProvider
            .predictionsAfter(request.context, request.script, request.limit)
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(request.limit)
            .toList()

    suspend fun preload() {
        if (predictionEnabled()) predictionProvider.preload()
    }
}

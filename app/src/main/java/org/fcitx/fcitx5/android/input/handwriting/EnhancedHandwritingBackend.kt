/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val MlKitHandwritingDispatcher: CoroutineDispatcher =
    Dispatchers.Default.limitedParallelism(1)

internal interface EnhancedHandwritingEngine {
    suspend fun isDownloaded(): Boolean

    suspend fun warmup()

    suspend fun recognize(
        strokes: List<HandwritingStroke>,
        limit: Int
    ): List<HandwritingRecognition>

    fun close()
}

internal interface EnhancedHandwritingBackend {
    suspend fun prepare(): Boolean

    suspend fun recognize(
        strokes: List<HandwritingStroke>,
        limit: Int
    ): List<HandwritingRecognition>

    fun close()
}

/** Keeps every ML Kit entry point off the latency-sensitive input thread. */
internal class MlKitEnhancedHandwritingBackend(
    private val dispatcher: CoroutineDispatcher = MlKitHandwritingDispatcher,
    private val engineFactory: () -> EnhancedHandwritingEngine = { MlKitHandwritingRecognizer() }
) : EnhancedHandwritingBackend {
    @Volatile
    private var engine: EnhancedHandwritingEngine? = null

    override suspend fun prepare(): Boolean = withContext(dispatcher) {
        val workerEngine = engine()
        if (!workerEngine.isDownloaded()) {
            false
        } else {
            workerEngine.warmup()
            true
        }
    }

    override suspend fun recognize(
        strokes: List<HandwritingStroke>,
        limit: Int
    ): List<HandwritingRecognition> = withContext(dispatcher) {
        engine().recognize(strokes, limit)
    }

    override fun close() {
        engine?.close()
        engine = null
    }

    private fun engine(): EnhancedHandwritingEngine = engine ?: engineFactory().also {
        // Model identifiers, Firebase registration, and native client setup are intentionally
        // first touched on this worker; constructing them from a toolbar click blocked input.
        engine = it
    }
}

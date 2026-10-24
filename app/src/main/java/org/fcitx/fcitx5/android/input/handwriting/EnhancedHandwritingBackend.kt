/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val MlKitHandwritingDispatcher: CoroutineDispatcher =
    Dispatchers.Default.limitedParallelism(1)

internal interface EnhancedHandwritingEngine {
    suspend fun isDownloaded(): Boolean

    suspend fun warmup()

    suspend fun recognize(request: HandwritingRecognitionRequest): List<HandwritingRecognition>

    fun close()
}

internal interface EnhancedHandwritingBackend {
    suspend fun prepare(language: HandwritingLanguage): Boolean

    suspend fun recognize(request: HandwritingRecognitionRequest): List<HandwritingRecognition>

    fun close()
}

/** Keeps every ML Kit entry point off the latency-sensitive input thread. */
internal class MlKitEnhancedHandwritingBackend(
    private val dispatcher: CoroutineDispatcher = MlKitHandwritingDispatcher,
    private val engineFactory: (HandwritingLanguage) -> EnhancedHandwritingEngine = {
        MlKitHandwritingRecognizer(it)
    }
) : EnhancedHandwritingBackend {
    private val engines = mutableMapOf<HandwritingLanguage, EnhancedHandwritingEngine>()
    private var closed = false

    override suspend fun prepare(language: HandwritingLanguage): Boolean = withContext(dispatcher) {
        val workerEngine = engine(language)
        if (!workerEngine.isDownloaded()) {
            false
        } else {
            workerEngine.warmup()
            true
        }
    }

    override suspend fun recognize(
        request: HandwritingRecognitionRequest
    ): List<HandwritingRecognition> = withContext(dispatcher) {
        engine(request.language).recognize(request)
    }

    override fun close() {
        // Service teardown calls this on the main thread. Releasing on the model lane keeps the
        // engines single-owner, and the flag stops a queued request from recreating a client.
        CoroutineScope(dispatcher).launch {
            closed = true
            engines.values.forEach(EnhancedHandwritingEngine::close)
            engines.clear()
        }
    }

    private fun engine(language: HandwritingLanguage): EnhancedHandwritingEngine {
        check(!closed) { "Enhanced handwriting backend is closed" }
        return engines[language] ?: engineFactory(language).also {
            // Model identifiers, Firebase registration, and native client setup are intentionally
            // first touched on this worker; constructing them from a toolbar click blocked input.
            engines[language] = it
        }
    }
}

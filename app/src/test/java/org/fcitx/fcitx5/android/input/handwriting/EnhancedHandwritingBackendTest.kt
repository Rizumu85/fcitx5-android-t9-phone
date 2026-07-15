/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedHandwritingBackendTest {
    @Test
    fun engineIsLazyAndAllExpensiveWorkUsesWorkerDispatcher() {
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, WorkerThreadName) }
        executor.asCoroutineDispatcher().use { dispatcher ->
            val engine = RecordingEngine(downloaded = true)
            var factoryCalls = 0
            val backend = MlKitEnhancedHandwritingBackend(dispatcher) {
                factoryCalls++
                engine.recordFactoryThread()
                engine
            }

            assertEquals(0, factoryCalls)

            runBlocking {
                assertTrue(backend.prepare())
                assertEquals(
                    listOf(HandwritingRecognition("字", 1f)),
                    backend.recognize(listOf(stroke()), limit = 1)
                )
            }

            assertEquals(1, factoryCalls)
            assertTrue(
                engine.workerThreads.toString(),
                engine.workerThreads.all { it.startsWith(WorkerThreadName) }
            )
            assertEquals(1, engine.warmupCalls)
            assertEquals(1, engine.recognitionCalls)
            backend.close()
        }
    }

    @Test
    fun missingModelDoesNotRunNativeWarmup() {
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, WorkerThreadName) }
        executor.asCoroutineDispatcher().use { dispatcher ->
            val engine = RecordingEngine(downloaded = false)
            val backend = MlKitEnhancedHandwritingBackend(dispatcher) { engine }

            runBlocking {
                assertFalse(backend.prepare())
            }

            assertEquals(0, engine.warmupCalls)
            backend.close()
        }
    }

    private class RecordingEngine(
        private val downloaded: Boolean
    ) : EnhancedHandwritingEngine {
        val workerThreads = mutableListOf<String>()
        var warmupCalls = 0
        var recognitionCalls = 0

        fun recordFactoryThread() {
            workerThreads += Thread.currentThread().name
        }

        override suspend fun isDownloaded(): Boolean {
            workerThreads += Thread.currentThread().name
            return downloaded
        }

        override suspend fun warmup() {
            workerThreads += Thread.currentThread().name
            warmupCalls++
        }

        override suspend fun recognize(
            strokes: List<HandwritingStroke>,
            limit: Int
        ): List<HandwritingRecognition> {
            workerThreads += Thread.currentThread().name
            recognitionCalls++
            return listOf(HandwritingRecognition("字", 1f)).take(limit)
        }

        override fun close() = Unit
    }

    private fun stroke() = HandwritingStroke(
        listOf(HandwritingPoint(1f, 1f, 0L))
    )

    private companion object {
        const val WorkerThreadName = "handwriting-model-worker"
    }
}

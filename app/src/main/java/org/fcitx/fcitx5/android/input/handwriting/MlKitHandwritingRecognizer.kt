/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitHandwritingRecognizer {
    private val modelIdentifier = requireNotNull(
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(ModelLanguageTag)
    )
    private val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
    private val modelManager = RemoteModelManager.getInstance()
    private var recognizer: DigitalInkRecognizer? = null

    suspend fun isDownloaded(): Boolean = modelManager.isModelDownloaded(model).await()

    suspend fun download() {
        modelManager.download(model, DownloadConditions.Builder().build()).await()
    }

    suspend fun warmup() {
        recognizeInk(
            Ink.builder()
                .addStroke(
                    Ink.Stroke.builder()
                        .addPoint(Ink.Point.create(0f, 0f, 0L))
                        .addPoint(Ink.Point.create(1f, 0f, 1L))
                        .build()
                )
                .build()
        )
    }

    suspend fun recognize(
        strokes: List<HandwritingStroke>,
        limit: Int
    ): List<HandwritingRecognition> {
        val ink = Ink.builder().apply {
            strokes.forEach { stroke ->
                val inkStroke = Ink.Stroke.builder().apply {
                    stroke.points.forEach { point ->
                        addPoint(Ink.Point.create(point.x, point.y, point.timeMillis))
                    }
                }.build()
                addStroke(inkStroke)
            }
        }.build()
        return recognizeInk(ink).candidates
            // This surface is a Chinese-character input mode. ML Kit also ranks strokes and
            // punctuation, but showing those here would displace the bundled Hanzi fallback.
            .filter { candidate -> candidate.text.isSingleHanCharacter() }
            .take(limit)
            .mapIndexed { index, candidate ->
                HandwritingRecognition(candidate.text, 1f / (index + 1f))
            }
    }

    private suspend fun recognizeInk(ink: Ink) = client().recognize(ink).await()

    private fun client(): DigitalInkRecognizer = recognizer ?: DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions.builder(model).build()
    ).also { recognizer = it }

    private fun String.isSingleHanCharacter(): Boolean {
        if (codePointCount(0, length) != 1) return false
        return Character.UnicodeScript.of(codePointAt(0)) == Character.UnicodeScript.HAN
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener { continuation.cancel() }
    }

    private companion object {
        const val ModelLanguageTag = "zh-Hani"
    }
}

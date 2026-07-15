/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink

internal class MlKitHandwritingRecognizer(
    private val modelManager: MlKitHandwritingModelManager = MlKitHandwritingModelManager()
) : EnhancedHandwritingEngine {
    private val model by lazy(modelManager::model)
    private var recognizer: DigitalInkRecognizer? = null

    override suspend fun isDownloaded(): Boolean = modelManager.isDownloaded()

    override suspend fun warmup() {
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

    override suspend fun recognize(
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

    override fun close() {
        recognizer?.close()
        recognizer = null
    }

}

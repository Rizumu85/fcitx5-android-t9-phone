/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea

internal class MlKitHandwritingRecognizer(
    private val language: HandwritingLanguage,
    private val modelManager: MlKitHandwritingModelManager = MlKitHandwritingModelManager(language)
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
        request: HandwritingRecognitionRequest
    ): List<HandwritingRecognition> {
        require(request.language == language)
        val ink = Ink.builder().apply {
            request.strokes.forEach { stroke ->
                val inkStroke = Ink.Stroke.builder().apply {
                    stroke.points.forEach { point ->
                        addPoint(Ink.Point.create(point.x, point.y, point.timeMillis))
                    }
                }.build()
                addStroke(inkStroke)
            }
        }.build()
        val context = buildMlKitRecognitionContext(request)
        return recognizeInk(ink, context).candidates
            .mapIndexedNotNull { index, candidate ->
                val text = HandwritingRecognitionTextPolicy.normalize(language, candidate.text)
                    ?: return@mapIndexedNotNull null
                // ML Kit candidate order is stable across models while score availability is not.
                HandwritingRecognition(text, 1f / (index + 1f))
            }
            .distinctBy(HandwritingRecognition::text)
            .take(request.limit)
    }

    private suspend fun recognizeInk(ink: Ink) = client().recognize(ink).await()

    private suspend fun recognizeInk(ink: Ink, context: RecognitionContext) =
        client().recognize(ink, context).await()

    private fun client(): DigitalInkRecognizer = recognizer ?: DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions.builder(model).build()
    ).also { recognizer = it }

    override fun close() {
        recognizer?.close()
        recognizer = null
    }
}

internal fun buildMlKitRecognitionContext(
    request: HandwritingRecognitionRequest
): RecognitionContext = RecognitionContext.builder().apply {
    request.writingArea?.let { area ->
        setWritingArea(WritingArea(area.width, area.height))
    }
    // ML Kit models pre-context as a required property even at the start of an editor.
    // Supplying the empty boundary keeps first-word recognition on the context-aware API.
    setPreContext(request.preContext.takeLast(MaximumPreContextLength))
}.build()

private const val MaximumPreContextLength = 20

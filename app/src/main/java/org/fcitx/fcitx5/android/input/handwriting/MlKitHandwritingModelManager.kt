/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Owns the downloadable model independently from an input-session recognizer. */
class MlKitHandwritingModelManager(
    val language: HandwritingLanguage,
    private val dispatcher: CoroutineDispatcher = MlKitHandwritingDispatcher
) {
    private val model by lazy {
        DigitalInkRecognitionModel.builder(
            requireNotNull(
                DigitalInkRecognitionModelIdentifier.fromLanguageTag(language.modelLanguageTag)
            )
        ).build()
    }

    private val remoteModelManager by lazy(RemoteModelManager::getInstance)

    internal fun model(): DigitalInkRecognitionModel = model

    suspend fun isDownloaded(): Boolean = withContext(dispatcher) {
        remoteModelManager.isModelDownloaded(model).await()
    }

    suspend fun download() = withContext(dispatcher) {
        remoteModelManager.download(model, DownloadConditions.Builder().build()).await()
    }

}

private val HandwritingLanguage.modelLanguageTag: String
    get() = when (this) {
        HandwritingLanguage.CHINESE -> "zh-Hani"
        HandwritingLanguage.ENGLISH -> "en"
    }

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(DirectExecutor) { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        when {
            task.isCanceled -> continuation.cancel()
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(
                task.exception ?: IllegalStateException("Google Task failed without an exception")
            )
        }
    }
}

private val DirectExecutor = Executor(Runnable::run)

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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Owns the downloadable model independently from an input-session recognizer. */
class MlKitHandwritingModelManager {
    val model: DigitalInkRecognitionModel = DigitalInkRecognitionModel.builder(
        requireNotNull(DigitalInkRecognitionModelIdentifier.fromLanguageTag(ModelLanguageTag))
    ).build()

    private val remoteModelManager = RemoteModelManager.getInstance()

    suspend fun isDownloaded(): Boolean = remoteModelManager.isModelDownloaded(model).await()

    suspend fun download() {
        remoteModelManager.download(model, DownloadConditions.Builder().build()).await()
    }

    private companion object {
        const val ModelLanguageTag = "zh-Hani"
    }
}

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener { continuation.cancel() }
}

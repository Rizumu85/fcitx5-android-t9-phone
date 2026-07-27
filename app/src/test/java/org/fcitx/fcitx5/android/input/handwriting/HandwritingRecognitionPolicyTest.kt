/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertEquals
import org.junit.Test

class HandwritingRecognitionPolicyTest {
    @Test
    fun chinesePrefersEnhancedUnlessTheModelIsKnownMissing() {
        assertEquals(
            HandwritingRecognitionBackend.ENHANCED,
            HandwritingRecognitionPolicy.selectBackend(
                HandwritingLanguage.CHINESE,
                enhancedModelKnownMissing = false
            )
        )
        assertEquals(
            HandwritingRecognitionBackend.OFFLINE,
            HandwritingRecognitionPolicy.selectBackend(
                HandwritingLanguage.CHINESE,
                enhancedModelKnownMissing = true
            )
        )
    }

    @Test
    fun englishNeverUsesTheChineseOfflineRecognizer() {
        assertEquals(
            HandwritingRecognitionBackend.ENHANCED,
            HandwritingRecognitionPolicy.selectBackend(
                HandwritingLanguage.ENGLISH,
                enhancedModelKnownMissing = true
            )
        )
    }

    @Test
    fun chineseFusionPreservesEnhancedOrderThenAddsOfflineRecall() {
        val enhanced = listOf(
            HandwritingRecognition("你", 1f),
            HandwritingRecognition("他", 0.5f)
        )
        val offline = listOf(
            HandwritingRecognition("他", 0.9f),
            HandwritingRecognition("们", 0.8f),
            HandwritingRecognition("好", 0.7f)
        )

        assertEquals(
            listOf("你", "他", "们"),
            HandwritingRecognitionPolicy.mergeChineseCandidates(
                enhanced,
                offline,
                limit = 3
            ).map(HandwritingRecognition::text)
        )
    }
}

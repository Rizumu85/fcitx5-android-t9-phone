/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertNotNull
import org.junit.Test

class MlKitHandwritingRecognizerTest {
    @Test
    fun contextAcceptsAnEmptyEditorBoundary() {
        val request = HandwritingRecognitionRequest(
            language = HandwritingLanguage.ENGLISH,
            strokes = emptyList(),
            writingArea = HandwritingWritingArea(width = 448f, height = 354f),
            preContext = "",
            limit = 10
        )

        assertNotNull(buildMlKitRecognitionContext(request))
    }
}

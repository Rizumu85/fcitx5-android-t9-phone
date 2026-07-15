/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingRecognitionTextPolicyTest {
    @Test
    fun acceptsHanziAndCommonPunctuation() {
        assertTrue(HandwritingRecognitionTextPolicy.accepts("写"))
        assertTrue(HandwritingRecognitionTextPolicy.accepts("，"))
        assertTrue(HandwritingRecognitionTextPolicy.accepts("。"))
        assertTrue(HandwritingRecognitionTextPolicy.accepts("?"))
        assertTrue(HandwritingRecognitionTextPolicy.accepts("+"))
    }

    @Test
    fun rejectsLatinWordsEmojiAndUnknownSymbolSequences() {
        assertFalse(HandwritingRecognitionTextPolicy.accepts("a"))
        assertFalse(HandwritingRecognitionTextPolicy.accepts("hello"))
        assertFalse(HandwritingRecognitionTextPolicy.accepts("🙂"))
        assertFalse(HandwritingRecognitionTextPolicy.accepts("..."))
    }
}

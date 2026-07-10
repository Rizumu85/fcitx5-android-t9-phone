/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class T9StrokeCodecTest {
    @Test
    fun displaysEverySupportedStrokeAndUnknownToken() {
        assertEquals("一丨丿丶乛？", T9StrokeCodec.display("123456"))
        assertNull(T9StrokeCodec.display("17"))
    }

    @Test
    fun literalCommitRequiresTheExactPreviewForTheRawCode() {
        assertEquals("一？", T9StrokeCodec.literalCommitText("16", "一？"))
        assertNull(T9StrokeCodec.literalCommitText("16", "一丨"))
        assertNull(T9StrokeCodec.literalCommitText("16", "一？一"))
    }

    @Test
    fun enginePreeditAcceptsCanonicalAndCompatibilityStrokeForms() {
        assertEquals("123456", T9StrokeCodec.digitsFromEnginePreedit("⼀㇑⼃㇏⼄？"))
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertEquals
import org.junit.Test

class HandwritingPreContextTest {
    @Test
    fun keepsLatestTwentyCodePointsAcrossEditorAndCommits() {
        val context = HandwritingPreContext()

        context.begin("A😀1234567890123456789")

        assertEquals("😀1234567890123456789", context.snapshot())

        context.append("中")

        assertEquals("1234567890123456789中", context.snapshot())
    }

    @Test
    fun clearStartsANewRecognitionBoundary() {
        val context = HandwritingPreContext()
        context.begin("中文")

        context.clear()
        context.append("新")

        assertEquals("新", context.snapshot())
    }
}

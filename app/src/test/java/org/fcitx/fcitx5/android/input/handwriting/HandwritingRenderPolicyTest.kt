/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingRenderPolicyTest {
    @Test
    fun fixedWidthPenUsesPrediction() {
        assertTrue(HandwritingRenderPolicy.usesPrediction(HandwritingBrushStyle.PEN))
    }

    @Test
    fun pressurePenAvoidsPredictedGeometryRebuilds() {
        assertFalse(HandwritingRenderPolicy.usesPrediction(HandwritingBrushStyle.CALLIGRAPHY))
    }
}

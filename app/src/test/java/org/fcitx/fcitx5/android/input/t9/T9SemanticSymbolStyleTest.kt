/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9SemanticSymbolStyleTest {
    @Test
    fun mapsReportedFontWeightToBoundedStrokeWidth() {
        assertEquals(0.070f, T9SemanticSymbolStyle.strokeEmForWeight(100), DELTA)
        assertEquals(0.0825f, T9SemanticSymbolStyle.strokeEmForWeight(250), DELTA)
        assertEquals(0.095f, T9SemanticSymbolStyle.strokeEmForWeight(400), DELTA)
        assertEquals(0.1175f, T9SemanticSymbolStyle.strokeEmForWeight(650), DELTA)
        assertEquals(0.140f, T9SemanticSymbolStyle.strokeEmForWeight(900), DELTA)
    }

    @Test
    fun clampsMalformedFontMetadata() {
        assertEquals(0.070f, T9SemanticSymbolStyle.strokeEmForWeight(-100), DELTA)
        assertEquals(0.140f, T9SemanticSymbolStyle.strokeEmForWeight(1200), DELTA)
    }

    companion object {
        private const val DELTA = 0.0001f
    }
}

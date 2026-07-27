/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9TextOpticalCenterTest {
    @Test
    fun representativeSampleIsStableForEachCompactInputScript() {
        assertEquals("hg", T9TextOpticalCenter.representativeSample("hei"))
        assertEquals("中", T9TextOpticalCenter.representativeSample("𠀀"))
        assertEquals("ㄅㄧ", T9TextOpticalCenter.representativeSample("ㄅㄧ"))
        assertEquals("…", T9TextOpticalCenter.representativeSample("…"))
    }

    @Test
    fun baselinePlacesVisibleGlyphBoundsAtContentCenter() {
        val baseline = T9TextOpticalCenter.centeredBaselinePx(
            contentCenterPx = 18f,
            sampleTopPx = -12,
            sampleBottomPx = 4
        )

        assertEquals(22f, baseline)
    }

    @Test
    fun textViewCorrectionUsesWholePixelsAndRespectsSafetyLimit() {
        assertEquals(
            4f,
            T9TextOpticalCenter.translationPx(
                currentBaselinePx = 18f,
                contentCenterPx = 18f,
                sampleTopPx = -12,
                sampleBottomPx = 4,
                maxAbsShiftPx = 6
            )
        )
        assertEquals(
            2f,
            T9TextOpticalCenter.translationPx(
                currentBaselinePx = 18f,
                contentCenterPx = 18f,
                sampleTopPx = -12,
                sampleBottomPx = 4,
                maxAbsShiftPx = 2
            )
        )
    }
}

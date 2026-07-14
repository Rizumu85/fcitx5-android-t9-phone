/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandwritingPronunciationFeedbackTest {
    @Test
    fun keepsEveryDistinctPolyphonicReadingInSourceOrder() {
        val feedback = HandwritingPronunciationFeedback.create(
            "行",
            listOf("xíng", " háng ", "xíng", "hàng")
        )

        assertEquals("行", feedback?.character)
        assertEquals(listOf("xíng", "háng", "hàng"), feedback?.readings)
    }

    @Test
    fun suppressesFeedbackWhenLookupHasNoReading() {
        assertNull(HandwritingPronunciationFeedback.create("字", listOf(" ")))
    }
}

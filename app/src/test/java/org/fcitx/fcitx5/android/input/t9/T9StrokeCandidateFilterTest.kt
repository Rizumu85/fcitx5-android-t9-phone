/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class T9StrokeCandidateFilterTest {

    @Test
    fun unsupportedGlyphIsRemovedBeforePagingAndOriginalIndicesSurvive() {
        val filter = T9StrokeCandidateFilter { it.text != "𬂛" }
        val source = paged("一", "𬂛", "下", cursor = 1)

        val filtered = filter.filter(source)

        assertEquals(listOf("一", "下"), filtered.data.candidates.map { it.text })
        assertArrayEquals(intArrayOf(0, 2), filtered.originalIndices)
        assertEquals(0, filtered.data.cursorIndex)
    }

    @Test
    fun supportedCursorMapsToItsFilteredPosition() {
        val filter = T9StrokeCandidateFilter { it.text != "𬂛" }

        val filtered = filter.filter(paged("一", "𬂛", "下", cursor = 2))

        assertEquals(1, filtered.data.cursorIndex)
        assertEquals(2, filtered.originalIndices[filtered.data.cursorIndex])
    }

    private fun paged(
        vararg words: String,
        cursor: Int
    ): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = words.map {
                FcitxEvent.Candidate(label = "", text = it, comment = "")
            }.toTypedArray(),
            cursorIndex = cursor,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class T9SmartEnglishPageCacheTest {

    @Test
    fun sameCandidatePageAndCursorReusesPagedResult() {
        val cache = cache()
        val data = paged("hello", "help", cursor = 1)

        val first = cache.build(data, contentKey = "words")
        val second = cache.build(data, contentKey = "words")

        assertSame(first, second)
        assertArrayEquals(intArrayOf(0, 1), second.originalIndices)
        assertEquals(1, second.data.cursorIndex)
    }

    @Test
    fun cursorChangeReusesPagerButBuildsNewPagedResult() {
        val cache = cache()

        cache.build(paged("hello", "help", cursor = 0), contentKey = "words")
        val second = cache.build(paged("hello", "help", cursor = 1), contentKey = "words")

        assertArrayEquals(intArrayOf(0, 1), second.originalIndices)
        assertEquals(1, second.data.cursorIndex)
    }

    @Test
    fun emptyCandidatesDoNotThrow() {
        val cache = cache()

        val result = cache.build(
            FcitxEvent.PagedCandidateEvent.Data(
                candidates = emptyArray(),
                cursorIndex = -1,
                layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
                hasPrev = false,
                hasNext = false
            ),
            contentKey = "empty"
        )

        assertEquals(0, result.data.candidates.size)
        assertArrayEquals(intArrayOf(), result.originalIndices)
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

    private fun cache(): T9SmartEnglishPageCache =
        T9SmartEnglishPageCache(
            characterBudget = { 20 },
            widthBudget = { null }
        )
}

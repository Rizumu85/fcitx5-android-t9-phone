/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateRendererTest {

    @Test
    fun firstRenderPatchesEveryRegion() {
        val patch = T9CandidateRenderer.diff(null, state())

        assertTrue(patch.preedit)
        assertTrue(patch.candidates)
        assertTrue(patch.candidateContent)
        assertTrue(patch.pinyin)
        assertTrue(patch.focus)
        assertTrue(patch.visibility)
    }

    @Test
    fun cursorOnlyChangePatchesCandidateRowOnly() {
        val previous = state(candidates = paged("a", cursor = 0))
        val next = state(candidates = paged("a", cursor = 1))

        val patch = T9CandidateRenderer.diff(previous, next)

        assertFalse(patch.preedit)
        assertTrue(patch.candidates)
        assertFalse(patch.candidateContent)
        assertFalse(patch.pinyin)
        assertFalse(patch.focus)
        assertFalse(patch.visibility)
    }

    @Test
    fun candidateContentChangeDoesNotRefreshPinyinContent() {
        val previous = state(candidates = paged("a"))
        val next = state(candidates = paged("alphabet"))

        val patch = T9CandidateRenderer.diff(previous, next)

        assertTrue(patch.candidates)
        assertTrue(patch.candidateContent)
        assertFalse(patch.pinyin)
        assertFalse(patch.focus)
    }

    @Test
    fun pinyinContentChangeRefreshesFocusForNewChips() {
        val previous = state(pinyinOptions = listOf("a"))
        val next = state(pinyinOptions = listOf("ai"))

        val patch = T9CandidateRenderer.diff(previous, next)

        assertTrue(patch.pinyin)
        assertTrue(patch.focus)
    }

    @Test
    fun reservedPreeditRowChangeOnlyPatchesPreedit() {
        val previous = state(reservePreeditRow = false)
        val next = state(reservePreeditRow = true)

        val patch = T9CandidateRenderer.diff(previous, next)

        assertTrue(patch.preedit)
        assertFalse(patch.candidates)
        assertFalse(patch.candidateContent)
        assertFalse(patch.pinyin)
        assertFalse(patch.focus)
        assertFalse(patch.visibility)
    }

    private fun state(
        candidates: FcitxEvent.PagedCandidateEvent.Data = paged("a"),
        pinyinOptions: List<String> = listOf("a"),
        reservePreeditRow: Boolean = false,
        shouldShow: Boolean = true
    ): T9CandidateRenderState =
        T9CandidateRenderState(
            panel = FcitxEvent.InputPanelEvent.Data(),
            candidates = candidates,
            orientation = FloatingCandidatesOrientation.Horizontal,
            showShortcutLabels = true,
            reservePreeditRow = reservePreeditRow,
            pinyinOptions = pinyinOptions,
            pinyinUseT9 = true,
            focus = T9CandidateFocus.BOTTOM,
            preferAboveCursorAnchor = true,
            shouldShow = shouldShow
        )

    private fun paged(
        text: String,
        cursor: Int = 0
    ): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = arrayOf(
                FcitxEvent.Candidate(label = "", text = text, comment = ""),
                FcitxEvent.Candidate(label = "", text = "b", comment = "")
            ),
            cursorIndex = cursor,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
}

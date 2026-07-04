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
    fun t9HiddenCommentChangeDoesNotPatchCandidateRow() {
        val previous = state(
            panel = panel("n"),
            candidates = paged("你", comment = "ni")
        )
        val next = state(
            panel = panel("ni"),
            candidates = paged("你", comment = "ni hao")
        )

        val patch = T9CandidateRenderer.diff(previous, next)

        assertTrue(patch.preedit)
        assertFalse(patch.candidates)
        assertFalse(patch.candidateContent)
        assertFalse(patch.pinyin)
        assertFalse(patch.visibility)
    }

    @Test
    fun nonT9CommentChangePatchesCandidateRow() {
        val previous = state(
            candidates = paged("你", comment = "ni"),
            showShortcutLabels = false
        )
        val next = state(
            candidates = paged("你", comment = "ni hao"),
            showShortcutLabels = false
        )

        val patch = T9CandidateRenderer.diff(previous, next)

        assertTrue(patch.candidates)
        assertTrue(patch.candidateContent)
    }

    @Test
    fun pinyinContentChangeRefreshesFocusForNewChips() {
        val previous = state(pinyinOptions = listOf("a"))
        val next = state(pinyinOptions = listOf("ai"))

        val patch = T9CandidateRenderer.diff(previous, next)

        assertTrue(patch.pinyin)
        assertTrue(patch.focus)
    }

    private fun state(
        panel: FcitxEvent.InputPanelEvent.Data = FcitxEvent.InputPanelEvent.Data(),
        candidates: FcitxEvent.PagedCandidateEvent.Data = paged("a"),
        pinyinOptions: List<String> = listOf("a"),
        shouldShow: Boolean = true,
        showShortcutLabels: Boolean = true
    ): T9CandidateRenderState =
        T9CandidateRenderState(
            panel = panel,
            candidates = candidates,
            orientation = FloatingCandidatesOrientation.Horizontal,
            showShortcutLabels = showShortcutLabels,
            pinyinOptions = pinyinOptions,
            pinyinUseT9 = true,
            focus = T9CandidateFocus.BOTTOM,
            preferAboveCursorAnchor = true,
            shouldShow = shouldShow
        )

    private fun paged(
        text: String,
        cursor: Int = 0,
        comment: String = ""
    ): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = arrayOf(
                FcitxEvent.Candidate(label = "", text = text, comment = comment),
                FcitxEvent.Candidate(label = "", text = "b", comment = "")
            ),
            cursorIndex = cursor,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )

    private fun panel(preedit: String): FcitxEvent.InputPanelEvent.Data =
        FcitxEvent.InputPanelEvent.Data(
            org.fcitx.fcitx5.android.core.FormattedText(
                strings = arrayOf(preedit),
                flags = intArrayOf(org.fcitx.fcitx5.android.core.TextFormatFlag.NoFlag.flag),
                cursor = -1
            ),
            org.fcitx.fcitx5.android.core.FormattedText.Empty,
            org.fcitx.fcitx5.android.core.FormattedText.Empty
        )
}

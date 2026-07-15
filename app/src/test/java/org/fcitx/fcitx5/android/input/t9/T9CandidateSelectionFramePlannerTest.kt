/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class T9CandidateSelectionFramePlannerTest {

    @Test
    fun cursorAndPreviewChangeProduceLocalFrame() {
        val previous = state(paged(cursor = 0, "你", "呢"), listOf("ni"))
        val nextCandidates = paged(cursor = 1, "你", "呢")

        val frame = T9CandidateSelectionFramePlanner.plan(
            previous = previous,
            sourcePanel = FcitxEvent.InputPanelEvent.Data(),
            candidates = nextCandidates,
            presentation = T9PresentationState(
                topReading = formatted("ne"),
                readingOptions = listOf("ni")
            )
        )

        assertNotNull(frame)
        assertEquals(1, frame?.renderState?.candidates?.cursorIndex)
        assertEquals("ne", frame?.renderState?.panel?.preedit?.toString())
    }

    @Test
    fun candidateContentChangeRequiresCompleteSnapshot() {
        val previous = state(paged(cursor = 0, "你", "呢"), listOf("ni"))

        val frame = T9CandidateSelectionFramePlanner.plan(
            previous = previous,
            sourcePanel = FcitxEvent.InputPanelEvent.Data(),
            candidates = paged(cursor = 0, "年"),
            presentation = T9PresentationState(formatted("nian"), listOf("ni"))
        )

        assertNull(frame)
    }

    @Test
    fun readingRowChangeRequiresCompleteSnapshot() {
        val previous = state(paged(cursor = 0, "你", "呢"), listOf("ni"))

        val frame = T9CandidateSelectionFramePlanner.plan(
            previous = previous,
            sourcePanel = FcitxEvent.InputPanelEvent.Data(),
            candidates = paged(cursor = 1, "你", "呢"),
            presentation = T9PresentationState(formatted("ne"), listOf("ne"))
        )

        assertNull(frame)
    }

    private fun state(
        candidates: FcitxEvent.PagedCandidateEvent.Data,
        readings: List<String>
    ) = T9CandidateRenderState(
        panel = FcitxEvent.InputPanelEvent.Data(
            formatted("ni"),
            FormattedText.Empty,
            FormattedText.Empty
        ),
        candidates = candidates,
        orientation = FloatingCandidatesOrientation.Horizontal,
        showShortcutLabels = true,
        shortcutStyle = T9ShortcutCandidateStyle.ADAPTIVE_TAIL,
        reservePreeditRow = false,
        readingOptions = readings,
        pinyinUseT9 = true,
        focus = T9CandidateFocus.BOTTOM,
        preferAboveInputPanel = false,
        shouldShow = true
    )

    private fun paged(
        cursor: Int,
        vararg candidates: String
    ) = FcitxEvent.PagedCandidateEvent.Data(
        candidates = candidates.map { FcitxEvent.Candidate("", it, "") }.toTypedArray(),
        cursorIndex = cursor,
        layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
        hasPrev = false,
        hasNext = false
    )

    private fun formatted(text: String) = FormattedText(
        strings = arrayOf(text),
        flags = intArrayOf(TextFormatFlag.NoFlag.flag),
        cursor = -1
    )
}

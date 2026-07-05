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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateRenderStatePlannerTest {

    @Test
    fun topReadingReplacesPreeditButKeepsAuxiliaryRows() {
        val planned = T9CandidateRenderStatePlanner.plan(
            input(
                inputPanel = FcitxEvent.InputPanelEvent.Data(
                    text("raw"),
                    text("aux-up"),
                    text("aux-down")
                ),
                presentationState = T9PresentationState(
                    topReading = text("preview"),
                    pinyinOptions = emptyList()
                )
            )
        )

        assertEquals("preview", planned.panel.preedit.toString())
        assertEquals("aux-up", planned.panel.auxUp.toString())
        assertEquals("aux-down", planned.panel.auxDown.toString())
    }

    @Test
    fun suppressedCompositionClearsPanelAndHidesUi() {
        val planned = T9CandidateRenderStatePlanner.plan(
            input(
                inputPanel = FcitxEvent.InputPanelEvent.Data(
                    text("stale"),
                    FormattedText.Empty,
                    FormattedText.Empty
                ),
                suppressEmptyCandidates = true,
                presentationState = T9PresentationState(
                    topReading = text("stale"),
                    pinyinOptions = listOf("stale")
                )
            )
        )

        assertTrue(planned.panel.preedit.isEmpty())
        assertFalse(planned.shouldShow)
        assertTrue(planned.pinyinOptions.isEmpty())
    }

    @Test
    fun shortcutLabelsFollowT9OwnedCandidateSources() {
        val chinese = T9CandidateRenderStatePlanner.plan(
            input(chineseT9Active = true, usesSmartEnglish = false)
        )
        val smartEnglish = T9CandidateRenderStatePlanner.plan(
            input(chineseT9Active = false, usesSmartEnglish = true)
        )
        val plain = T9CandidateRenderStatePlanner.plan(
            input(chineseT9Active = false, usesSmartEnglish = false)
        )

        assertTrue(chinese.showShortcutLabels)
        assertTrue(smartEnglish.showShortcutLabels)
        assertFalse(plain.showShortcutLabels)
    }

    @Test
    fun chineseAndEnglishUseStableBubblePlacement() {
        val planned = T9CandidateRenderStatePlanner.plan(input(chineseT9Active = true))

        assertFalse(planned.preferAboveCursorAnchor)
    }

    @Test
    fun pinyinRowAloneKeepsUiVisible() {
        val planned = T9CandidateRenderStatePlanner.plan(
            input(
                candidates = FcitxEvent.PagedCandidateEvent.Data.Empty,
                presentationState = T9PresentationState(
                    topReading = null,
                    pinyinOptions = listOf("gao")
                )
            )
        )

        assertTrue(planned.shouldShow)
        assertEquals(listOf("gao"), planned.pinyinOptions)
    }

    private fun input(
        inputPanel: FcitxEvent.InputPanelEvent.Data = FcitxEvent.InputPanelEvent.Data(),
        candidates: FcitxEvent.PagedCandidateEvent.Data = paged("你"),
        usesSmartEnglish: Boolean = false,
        usesPendingPunctuation: Boolean = false,
        chineseT9Active: Boolean = true,
        suppressEmptyCandidates: Boolean = false,
        presentationState: T9PresentationState? = null,
        focus: T9CandidateFocus = T9CandidateFocus.BOTTOM
    ): T9CandidateRenderStatePlanner.Input =
        T9CandidateRenderStatePlanner.Input(
            inputPanel = inputPanel,
            candidates = candidates,
            orientation = FloatingCandidatesOrientation.Horizontal,
            usesSmartEnglish = usesSmartEnglish,
            usesPendingPunctuation = usesPendingPunctuation,
            chineseT9Active = chineseT9Active,
            suppressEmptyCandidates = suppressEmptyCandidates,
            presentationState = presentationState,
            focus = focus
        )

    private fun paged(text: String): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = arrayOf(FcitxEvent.Candidate(label = "", text = text, comment = "")),
            cursorIndex = 0,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )

    private fun text(value: String): FormattedText =
        FormattedText(
            strings = arrayOf(value),
            flags = intArrayOf(TextFormatFlag.NoFlag.flag),
            cursor = -1
        )
}

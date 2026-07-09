/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9CandidateRenderPassPlannerTest {

    @Test
    fun firstVisibleChinesePinyinFrameRendersPinyinRow() {
        val plan = T9CandidateRenderPassPlanner.plan(
            input(previous = null, next = state(pinyinOptions = listOf("ge", "he")))
        )

        assertEquals(T9CandidateRenderPassPlanner.PinyinAction.RENDER, plan.pinyinAction)
        assertFalse(plan.skipChildRender)
    }

    @Test
    fun candidateContentChangeWithReadyPinyinOnlySyncsPinyinLayout() {
        val previous = state(candidates = paged("好"), pinyinOptions = listOf("ge", "he"))
        val next = state(candidates = paged("好", "给"), pinyinOptions = listOf("ge", "he"))
        val plan = T9CandidateRenderPassPlanner.plan(
            input(
                previous = previous,
                next = next,
                previousVisibilityRequest = visibleRequest(contentReady = true)
            )
        )

        assertEquals(T9CandidateRenderPassPlanner.PinyinAction.SYNC_LAYOUT, plan.pinyinAction)
    }

    @Test
    fun pendingPinyinRevealRendersAgainUntilContentReady() {
        val previous = state(candidates = paged("好"), pinyinOptions = listOf("ge"))
        val next = state(candidates = paged("好"), pinyinOptions = listOf("ge"))
        val plan = T9CandidateRenderPassPlanner.plan(
            input(
                previous = previous,
                next = next,
                previousVisibilityRequest = visibleRequest(contentReady = false)
            )
        )

        assertEquals(T9CandidateRenderPassPlanner.PinyinAction.RENDER, plan.pinyinAction)
    }

    @Test
    fun nonChineseT9FrameClearsPreviouslyVisiblePinyinRow() {
        val previous = state(pinyinOptions = listOf("ge"))
        val next = state(pinyinUseT9 = false, pinyinOptions = emptyList())

        val plan = T9CandidateRenderPassPlanner.plan(
            input(previous = previous, next = next)
        )

        assertEquals(T9CandidateRenderPassPlanner.PinyinAction.CLEAR, plan.pinyinAction)
    }

    @Test
    fun hiddenFrameSkipsChildRenderingAndPlansHide() {
        val previous = state(pinyinOptions = listOf("ge"))
        val next = state(shouldShow = false, pinyinOptions = emptyList())

        val plan = T9CandidateRenderPassPlanner.plan(
            input(
                previous = previous,
                next = next,
                previousVisibilityRequest = visibleRequest(contentReady = true)
            )
        )

        assertTrue(plan.skipChildRender)
        assertEquals(T9CandidateVisibilityPlanner.Action.HIDE, plan.hiddenVisibilityAction)
        assertEquals(T9CandidateRenderPassPlanner.PinyinAction.NONE, plan.pinyinAction)
    }

    private fun input(
        previous: T9CandidateRenderState?,
        next: T9CandidateRenderState,
        previousVisibilityRequest: T9CandidateVisibilityPlanner.Request? = null
    ): T9CandidateRenderPassPlanner.Input =
        T9CandidateRenderPassPlanner.Input(
            previousState = previous,
            nextState = next,
            patch = T9CandidateRenderer.diff(previous, next),
            previousVisibilityRequest = previousVisibilityRequest
        )

    private fun visibleRequest(contentReady: Boolean): T9CandidateVisibilityPlanner.Request =
        T9CandidateVisibilityPlanner.Request(
            shouldShow = true,
            contentReady = contentReady,
            preferAboveCursorAnchor = false
        )

    private fun state(
        candidates: FcitxEvent.PagedCandidateEvent.Data = paged("好"),
        pinyinOptions: List<String> = listOf("ge"),
        pinyinUseT9: Boolean = true,
        shouldShow: Boolean = true
    ): T9CandidateRenderState =
        T9CandidateRenderState(
            panel = FcitxEvent.InputPanelEvent.Data(),
            candidates = candidates,
            orientation = FloatingCandidatesOrientation.Horizontal,
            showShortcutLabels = true,
            shortcutStyle = T9ShortcutCandidateStyle.ADAPTIVE_TAIL,
            reservePreeditRow = false,
            pinyinOptions = pinyinOptions,
            pinyinUseT9 = pinyinUseT9,
            focus = T9CandidateFocus.BOTTOM,
            preferAboveCursorAnchor = false,
            shouldShow = shouldShow
        )

    private fun paged(vararg words: String): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = words.map {
                FcitxEvent.Candidate(label = "", text = it, comment = "")
            }.toTypedArray(),
            cursorIndex = 0,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
}

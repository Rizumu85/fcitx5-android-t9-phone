/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class T9CandidateUiRendererTest {

    @Test
    fun visiblePinyinRowIsEnsuredOnEveryRender() {
        val delegate = FakeDelegate()
        val renderer = T9CandidateUiRenderer(delegate)

        renderer.render(state(candidates = paged("a")))
        renderer.render(state(candidates = paged("alphabet")))

        assertEquals(2, delegate.renderPinyinCount)
        assertEquals(0, delegate.syncPinyinLayoutCount)
    }

    @Test
    fun unchangedPendingVisibilityDoesNotRequestLayoutAgain() {
        val delegate = FakeDelegate(pinyinReady = false)
        val renderer = T9CandidateUiRenderer(delegate)

        renderer.render(state(candidates = paged("a")))
        renderer.render(state(candidates = paged("a")))

        assertEquals(listOf(false), delegate.showRequests)
        assertEquals(0, delegate.hideCount)
    }

    @Test
    fun pendingVisibilityRequestsAgainWhenEnsuredPinyinContentBecomesReady() {
        val delegate = FakeDelegate(pinyinReady = false)
        val renderer = T9CandidateUiRenderer(delegate)

        renderer.render(state(candidates = paged("a")))
        delegate.pinyinReady = true
        renderer.render(state(candidates = paged("a")))

        assertEquals(listOf(false, true), delegate.showRequests)
    }

    @Test
    fun visibleCandidateContentChangeDoesNotRequestVisibilityAgain() {
        val delegate = FakeDelegate()
        val renderer = T9CandidateUiRenderer(delegate)

        renderer.render(state(candidates = paged("a")))
        renderer.render(state(candidates = paged("alphabet")))

        assertEquals(listOf(true), delegate.showRequests)
    }

    @Test
    fun hidingPanelSkipsInternalPinyinRowCollapse() {
        val delegate = FakeDelegate()
        val renderer = T9CandidateUiRenderer(delegate)

        renderer.render(state(candidates = paged("a"), pinyinOptions = listOf("a")))
        renderer.render(state(candidates = emptyPaged(), pinyinOptions = emptyList(), shouldShow = false))

        assertEquals(listOf("renderPinyin", "show", "hide"), delegate.events)
        assertEquals(1, delegate.renderPinyinCount)
        assertEquals(1, delegate.hideCount)
    }

    @Test
    fun immediateHideDoesNotRenderChildRowsAndAllowsNextShow() {
        val delegate = FakeDelegate()
        val renderer = T9CandidateUiRenderer(delegate)

        val visible = state(candidates = paged("a"), pinyinOptions = listOf("a"))
        renderer.render(visible)
        renderer.hideImmediately()
        renderer.render(visible)

        assertEquals(listOf("renderPinyin", "show", "hide", "renderPinyin", "show"), delegate.events)
        assertEquals(2, delegate.renderPinyinCount)
        assertEquals(1, delegate.hideCount)
    }

    @Test
    fun nonChineseT9FrameClearsStalePinyinRow() {
        val delegate = FakeDelegate()
        val renderer = T9CandidateUiRenderer(delegate)

        renderer.render(
            state(
                candidates = paged("hello"),
                pinyinOptions = emptyList(),
                pinyinUseT9 = false
            )
        )

        assertEquals(listOf("renderPinyin", "show"), delegate.events)
        assertEquals(1, delegate.renderPinyinCount)
    }

    private class FakeDelegate : T9CandidateUiRenderer.Delegate {
        constructor()
        constructor(pinyinReady: Boolean) {
            this.pinyinReady = pinyinReady
        }

        var renderPinyinCount = 0
        var syncPinyinLayoutCount = 0
        var pinyinReady = true
        val showRequests = mutableListOf<Boolean>()
        var hideCount = 0
        val events = mutableListOf<String>()

        override fun setPreferAboveCursorAnchor(preferAboveCursorAnchor: Boolean) = Unit

        override fun renderPreedit(panel: FcitxEvent.InputPanelEvent.Data) = Unit

        override fun renderCandidates(
            candidates: FcitxEvent.PagedCandidateEvent.Data,
            orientation: FloatingCandidatesOrientation,
            showShortcutLabels: Boolean
        ) = Unit

        override fun renderPinyin(pinyinOptions: List<String>, pinyinUseT9: Boolean): Boolean {
            events += "renderPinyin"
            renderPinyinCount += 1
            return pinyinReady
        }

        override fun syncPinyinLayout(): Boolean {
            events += "syncPinyinLayout"
            syncPinyinLayoutCount += 1
            return pinyinReady
        }

        override fun renderFocus(focus: T9CandidateFocus) = Unit

        override fun showWhenPositioned(contentReady: Boolean) {
            events += "show"
            showRequests += contentReady
        }

        override fun hideCandidateUi() {
            events += "hide"
            hideCount += 1
        }
    }

    private fun state(
        candidates: FcitxEvent.PagedCandidateEvent.Data,
        pinyinOptions: List<String> = listOf("a"),
        shouldShow: Boolean = true,
        pinyinUseT9: Boolean = true
    ): T9CandidateRenderState =
        T9CandidateRenderState(
            panel = FcitxEvent.InputPanelEvent.Data(),
            candidates = candidates,
            orientation = FloatingCandidatesOrientation.Horizontal,
            showShortcutLabels = true,
            pinyinOptions = pinyinOptions,
            pinyinUseT9 = pinyinUseT9,
            focus = T9CandidateFocus.BOTTOM,
            preferAboveCursorAnchor = true,
            shouldShow = shouldShow
        )

    private fun paged(text: String): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data(
            candidates = arrayOf(
                FcitxEvent.Candidate(label = "", text = text, comment = ""),
                FcitxEvent.Candidate(label = "", text = "b", comment = "")
            ),
            cursorIndex = 0,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )

    private fun emptyPaged(): FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data.Empty
}

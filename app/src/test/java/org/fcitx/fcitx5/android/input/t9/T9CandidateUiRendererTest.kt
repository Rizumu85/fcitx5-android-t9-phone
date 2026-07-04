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
    fun candidateContentChangeSyncsPinyinLayoutWithoutRebindingPinyinContent() {
        val delegate = FakeDelegate()
        val renderer = T9CandidateUiRenderer(delegate)

        renderer.render(state(candidates = paged("a")))
        renderer.render(state(candidates = paged("alphabet")))

        assertEquals(1, delegate.renderPinyinCount)
        assertEquals(1, delegate.syncPinyinLayoutCount)
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
    fun pendingVisibilityRequestsAgainWhenContentBecomesReady() {
        val delegate = FakeDelegate(pinyinReady = false)
        val renderer = T9CandidateUiRenderer(delegate)

        renderer.render(state(candidates = paged("a")))
        delegate.pinyinReady = true
        renderer.render(state(candidates = paged("alphabet")))

        assertEquals(listOf(false, true), delegate.showRequests)
    }

    @Test
    fun hidingPanelSkipsInternalPinyinCollapse() {
        val delegate = FakeDelegate()
        val renderer = T9CandidateUiRenderer(delegate)

        renderer.render(state(candidates = paged("a")))
        renderer.render(state(
            candidates = FcitxEvent.PagedCandidateEvent.Data.Empty,
            pinyinOptions = emptyList(),
            pinyinUseT9 = false,
            shouldShow = false
        ))

        assertEquals(1, delegate.renderPinyinCount)
        assertEquals(1, delegate.hideCount)
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

        override fun setPreferAboveCursorAnchor(preferAboveCursorAnchor: Boolean) = Unit

        override fun renderPreedit(panel: FcitxEvent.InputPanelEvent.Data) = Unit

        override fun renderCandidates(
            candidates: FcitxEvent.PagedCandidateEvent.Data,
            orientation: FloatingCandidatesOrientation,
            showShortcutLabels: Boolean
        ) = Unit

        override fun renderPinyin(pinyinOptions: List<String>, pinyinUseT9: Boolean): Boolean {
            renderPinyinCount += 1
            return pinyinReady
        }

        override fun syncPinyinLayout(): Boolean {
            syncPinyinLayoutCount += 1
            return pinyinReady
        }

        override fun renderFocus(focus: T9CandidateFocus) = Unit

        override fun showWhenPositioned(contentReady: Boolean) {
            showRequests += contentReady
        }

        override fun hideCandidateUi() {
            hideCount += 1
        }
    }

    private fun state(
        candidates: FcitxEvent.PagedCandidateEvent.Data,
        pinyinOptions: List<String> = listOf("a"),
        pinyinUseT9: Boolean = true,
        shouldShow: Boolean = true
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
}

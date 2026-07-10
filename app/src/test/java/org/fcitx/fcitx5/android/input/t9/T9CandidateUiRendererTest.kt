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
    fun readyVisiblePinyinRowSyncsLayoutForCandidateContentChanges() {
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
        assertEquals(1, delegate.syncPinyinLayoutCount)
    }

    @Test
    fun hidingPanelSkipsInternalPinyinCollapse() {
        val delegate = FakeDelegate()
        val renderer = T9CandidateUiRenderer(delegate)

        renderer.render(state(candidates = paged("a")))
        renderer.render(state(
            candidates = emptyPaged(),
            readingOptions = emptyList(),
            shouldShow = false
        ))

        assertEquals(listOf("renderPinyin", "show", "hide"), delegate.events)
        assertEquals(1, delegate.hideCount)
    }

    @Test
    fun immediateHideDoesNotRenderChildRowsAndAllowsNextShow() {
        val delegate = FakeDelegate()
        val renderer = T9CandidateUiRenderer(delegate)

        val visible = state(candidates = paged("a"), readingOptions = listOf("a"))
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
                readingOptions = emptyList(),
                pinyinUseT9 = false
            )
        )

        assertEquals(listOf("renderPinyin", "show"), delegate.events)
        assertEquals(1, delegate.renderPinyinCount)
    }

    @Test
    fun candidateStatusChangeIsForwardedToTheSurface() {
        val delegate = FakeDelegate()
        val renderer = T9CandidateUiRenderer(delegate)

        renderer.render(
            state(
                candidates = emptyPaged(),
                readingOptions = emptyList(),
                candidateStatus = T9CandidateStatus.NO_MATCH
            )
        )

        assertEquals(listOf(T9CandidateStatus.NO_MATCH), delegate.candidateStatuses)
    }

    @Test
    fun localSelectionUpdatesOnlyCandidateSelectionAndPreedit() {
        val delegate = FakeDelegate()
        val renderer = T9CandidateUiRenderer(delegate)
        val initial = state(candidates = paged("a"))
        renderer.render(initial)
        delegate.events.clear()

        val rendered = renderer.renderSelection(
            T9CandidateSelectionFrame(
                initial.copy(
                    panel = FcitxEvent.InputPanelEvent.Data(
                        initial.panel.preedit,
                        initial.panel.auxUp,
                        initial.panel.auxDown
                    ),
                    candidates = initial.candidates.copy(cursorIndex = 1)
                )
            )
        )

        assertEquals(true, rendered)
        assertEquals(1, delegate.selectionRenderCount)
        assertEquals(emptyList<String>(), delegate.events)
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
        val candidateStatuses = mutableListOf<T9CandidateStatus?>()
        var selectionRenderCount = 0

        override fun setPreferAboveCursorAnchor(preferAboveCursorAnchor: Boolean) = Unit

        override fun renderPreedit(panel: FcitxEvent.InputPanelEvent.Data, reserveRow: Boolean) = Unit

        override fun renderCandidates(
            candidates: FcitxEvent.PagedCandidateEvent.Data,
            orientation: FloatingCandidatesOrientation,
            showShortcutLabels: Boolean,
            shortcutStyle: T9ShortcutCandidateStyle,
            candidateStatus: T9CandidateStatus?
        ) {
            candidateStatuses += candidateStatus
        }

        override fun renderCandidateSelection(
            candidates: FcitxEvent.PagedCandidateEvent.Data
        ): Boolean {
            selectionRenderCount += 1
            return true
        }

        override fun renderPinyin(readingOptions: List<String>, pinyinUseT9: Boolean): Boolean {
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
        readingOptions: List<String> = listOf("a"),
        pinyinUseT9: Boolean = true,
        shouldShow: Boolean = true,
        candidateStatus: T9CandidateStatus? = null
    ): T9CandidateRenderState =
        T9CandidateRenderState(
            panel = FcitxEvent.InputPanelEvent.Data(),
            candidates = candidates,
            orientation = FloatingCandidatesOrientation.Horizontal,
            showShortcutLabels = true,
            shortcutStyle = T9ShortcutCandidateStyle.ADAPTIVE_TAIL,
            candidateStatus = candidateStatus,
            reservePreeditRow = false,
            readingOptions = readingOptions,
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

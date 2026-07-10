/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation

class T9CandidateUiRenderer(
    private val delegate: Delegate
) {
    interface Delegate {
        fun setPreferAboveCursorAnchor(preferAboveCursorAnchor: Boolean)
        fun renderPreedit(panel: FcitxEvent.InputPanelEvent.Data, reserveRow: Boolean)
        fun renderCandidates(
            candidates: FcitxEvent.PagedCandidateEvent.Data,
            orientation: FloatingCandidatesOrientation,
            showShortcutLabels: Boolean,
            shortcutStyle: T9ShortcutCandidateStyle,
            candidateStatus: T9CandidateStatus?
        )
        fun renderPinyin(readingOptions: List<String>, pinyinUseT9: Boolean): Boolean
        fun syncPinyinLayout(): Boolean
        fun renderFocus(focus: T9CandidateFocus)
        fun showWhenPositioned(contentReady: Boolean)
        fun hideCandidateUi()
    }

    private var previousState: T9CandidateRenderState? = null
    private var previousVisibilityRequest: T9CandidateVisibilityPlanner.Request? = null

    fun reset() {
        previousState = null
        previousVisibilityRequest = null
    }

    fun hideImmediately() {
        delegate.hideCandidateUi()
        previousVisibilityRequest = T9CandidateVisibilityPlanner.Request(
            shouldShow = false,
            contentReady = true,
            preferAboveCursorAnchor = previousVisibilityRequest?.preferAboveCursorAnchor ?: false
        )
    }

    fun render(next: T9CandidateRenderState) {
        val patch = T9CandidateRenderer.diff(previousState, next)
        val renderPass = T9CandidateRenderPassPlanner.plan(
            T9CandidateRenderPassPlanner.Input(
                previousState = previousState,
                nextState = next,
                patch = patch,
                previousVisibilityRequest = previousVisibilityRequest
            )
        )
        if (renderPass.skipChildRender) {
            when (renderPass.hiddenVisibilityAction) {
                T9CandidateVisibilityPlanner.Action.HIDE ->
                    T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderVisibility") {
                        delegate.hideCandidateUi()
                    }
                T9CandidateVisibilityPlanner.Action.SHOW,
                T9CandidateVisibilityPlanner.Action.NONE -> Unit
            }
            previousVisibilityRequest = renderPass.hiddenVisibilityRequest
            previousState = next
            return
        }
        if (previousState?.preferAboveCursorAnchor != next.preferAboveCursorAnchor) {
            delegate.setPreferAboveCursorAnchor(next.preferAboveCursorAnchor)
        }
        if (patch.preedit) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPreedit") {
                delegate.renderPreedit(next.panel, next.reservePreeditRow)
            }
        }
        if (patch.candidates) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderCandidates") {
                delegate.renderCandidates(
                    candidates = next.candidates,
                    orientation = next.orientation,
                    showShortcutLabels = next.showShortcutLabels,
                    shortcutStyle = next.shortcutStyle,
                    candidateStatus = next.candidateStatus
                )
            }
        }
        val pinyinRowReady = when (renderPass.pinyinAction) {
            T9CandidateRenderPassPlanner.PinyinAction.CLEAR -> {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin") {
                    delegate.renderPinyin(emptyList(), false)
                }
            }
            T9CandidateRenderPassPlanner.PinyinAction.RENDER -> {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin") {
                    delegate.renderPinyin(next.readingOptions, next.pinyinUseT9)
                }
            }
            T9CandidateRenderPassPlanner.PinyinAction.SYNC_LAYOUT -> {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyinLayout") {
                    delegate.syncPinyinLayout()
                }
            }
            T9CandidateRenderPassPlanner.PinyinAction.NONE -> renderPass.fallbackContentReady
        }
        if (patch.focus) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderFocus") {
                delegate.renderFocus(next.focus)
            }
        }
        val nextVisibilityRequest = T9CandidateRenderPassPlanner.visibleRequest(
            nextState = next,
            contentReady = pinyinRowReady
        )
        when (T9CandidateVisibilityPlanner.plan(previousVisibilityRequest, nextVisibilityRequest)) {
            T9CandidateVisibilityPlanner.Action.SHOW ->
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderVisibility") {
                    delegate.showWhenPositioned(pinyinRowReady)
                }
            T9CandidateVisibilityPlanner.Action.HIDE ->
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderVisibility") {
                    delegate.hideCandidateUi()
                }
            T9CandidateVisibilityPlanner.Action.NONE -> Unit
        }
        previousVisibilityRequest = nextVisibilityRequest
        previousState = next
    }
}

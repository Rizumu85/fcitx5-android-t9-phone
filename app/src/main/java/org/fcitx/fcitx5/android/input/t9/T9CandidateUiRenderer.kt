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
            showShortcutLabels: Boolean
        )
        fun renderPinyin(pinyinOptions: List<String>, pinyinUseT9: Boolean): Boolean
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
        val visibilityRequest = T9CandidateVisibilityPlanner.Request(
            shouldShow = next.shouldShow,
            contentReady = previousVisibilityRequest?.contentReady ?: true,
            preferAboveCursorAnchor = next.preferAboveCursorAnchor
        )
        if (!next.shouldShow) {
            when (T9CandidateVisibilityPlanner.plan(previousVisibilityRequest, visibilityRequest)) {
                T9CandidateVisibilityPlanner.Action.HIDE ->
                    T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderVisibility") {
                        delegate.hideCandidateUi()
                    }
                T9CandidateVisibilityPlanner.Action.SHOW,
                T9CandidateVisibilityPlanner.Action.NONE -> Unit
            }
            previousVisibilityRequest = visibilityRequest
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
                    showShortcutLabels = next.showShortcutLabels
                )
            }
        }
        val shouldClearPinyinRow = !next.pinyinUseT9 &&
            (previousState == null || previousState?.pinyinUseT9 == true || previousState?.pinyinOptions?.isNotEmpty() == true)
        val shouldEnsurePinyinRow = next.pinyinUseT9 && next.pinyinOptions.isNotEmpty()
        val pinyinRowReady = when {
            shouldClearPinyinRow -> {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin") {
                    delegate.renderPinyin(emptyList(), false)
                }
            }
            patch.pinyin || shouldEnsurePinyinRow -> {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin") {
                    delegate.renderPinyin(next.pinyinOptions, next.pinyinUseT9)
                }
            }
            patch.candidateContent && next.pinyinUseT9 && next.pinyinOptions.isNotEmpty() -> {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyinLayout") {
                    delegate.syncPinyinLayout()
                }
            }
            else -> previousVisibilityRequest?.contentReady ?: true
        }
        if (patch.focus) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderFocus") {
                delegate.renderFocus(next.focus)
            }
        }
        val nextVisibilityRequest = T9CandidateVisibilityPlanner.Request(
            shouldShow = next.shouldShow,
            contentReady = pinyinRowReady,
            preferAboveCursorAnchor = next.preferAboveCursorAnchor
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

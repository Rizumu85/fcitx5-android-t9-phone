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
    private data class VisibilityRequest(
        val shouldShow: Boolean,
        val contentReady: Boolean,
        val preferAboveCursorAnchor: Boolean
    )

    interface Delegate {
        fun setPreferAboveCursorAnchor(preferAboveCursorAnchor: Boolean)
        fun renderPreedit(panel: FcitxEvent.InputPanelEvent.Data)
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
    private var previousVisibilityRequest: VisibilityRequest? = null

    fun reset() {
        previousState = null
        previousVisibilityRequest = null
    }

    fun render(next: T9CandidateRenderState) {
        val patch = T9CandidateRenderer.diff(previousState, next)
        val hiddenVisibilityRequest = VisibilityRequest(
            shouldShow = false,
            contentReady = true,
            preferAboveCursorAnchor = next.preferAboveCursorAnchor
        )
        if (!next.shouldShow) {
            if (patch.visibility || previousVisibilityRequest != hiddenVisibilityRequest) {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderVisibility") {
                    delegate.hideCandidateUi()
                }
                previousVisibilityRequest = hiddenVisibilityRequest
            }
            previousState = next
            return
        }
        if (previousState?.preferAboveCursorAnchor != next.preferAboveCursorAnchor) {
            delegate.setPreferAboveCursorAnchor(next.preferAboveCursorAnchor)
        }
        if (patch.preedit) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPreedit") {
                delegate.renderPreedit(next.panel)
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
        val pinyinRowReady = when {
            patch.pinyin -> {
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
        val visibilityRequest = VisibilityRequest(
            shouldShow = next.shouldShow,
            contentReady = pinyinRowReady,
            preferAboveCursorAnchor = next.preferAboveCursorAnchor
        )
        if (patch.visibility || previousVisibilityRequest != visibilityRequest) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderVisibility") {
                if (next.shouldShow) {
                    delegate.showWhenPositioned(pinyinRowReady)
                } else {
                    delegate.hideCandidateUi()
                }
            }
            previousVisibilityRequest = visibilityRequest
        }
        previousState = next
    }
}

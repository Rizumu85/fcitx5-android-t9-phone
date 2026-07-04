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
    ) {
        fun requiresRenderAfter(previous: VisibilityRequest?): Boolean {
            if (previous == null) return true
            if (shouldShow != previous.shouldShow) return true
            if (!shouldShow) return false
            if (!previous.contentReady && contentReady) return true
            return preferAboveCursorAnchor != previous.preferAboveCursorAnchor
        }
    }

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

    fun hideImmediately() {
        delegate.hideCandidateUi()
        previousVisibilityRequest = VisibilityRequest(
            shouldShow = false,
            contentReady = true,
            preferAboveCursorAnchor = previousVisibilityRequest?.preferAboveCursorAnchor ?: false
        )
    }

    fun render(next: T9CandidateRenderState) {
        val patch = T9CandidateRenderer.diff(previousState, next)
        val visibilityRequest = VisibilityRequest(
            shouldShow = next.shouldShow,
            contentReady = previousVisibilityRequest?.contentReady ?: true,
            preferAboveCursorAnchor = next.preferAboveCursorAnchor
        )
        if (!next.shouldShow) {
            if (visibilityRequest.requiresRenderAfter(previousVisibilityRequest)) {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderVisibility") {
                    delegate.hideCandidateUi()
                }
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
        val nextVisibilityRequest = VisibilityRequest(
            shouldShow = next.shouldShow,
            contentReady = pinyinRowReady,
            preferAboveCursorAnchor = next.preferAboveCursorAnchor
        )
        if (nextVisibilityRequest.requiresRenderAfter(previousVisibilityRequest)) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderVisibility") {
                delegate.showWhenPositioned(pinyinRowReady)
            }
        }
        previousVisibilityRequest = nextVisibilityRequest
        previousState = next
    }
}

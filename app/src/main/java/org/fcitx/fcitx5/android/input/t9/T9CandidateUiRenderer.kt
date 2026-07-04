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
        fun renderPreedit(panel: FcitxEvent.InputPanelEvent.Data)
        fun renderCandidates(
            candidates: FcitxEvent.PagedCandidateEvent.Data,
            orientation: FloatingCandidatesOrientation,
            showShortcutLabels: Boolean
        )
        fun renderPinyin(pinyinOptions: List<String>, pinyinUseT9: Boolean): Boolean
        fun renderFocus()
        fun showWhenPositioned(contentReady: Boolean)
        fun hideCandidateUi()
    }

    private var previousState: T9CandidateRenderState? = null

    fun reset() {
        previousState = null
    }

    fun render(next: T9CandidateRenderState) {
        val patch = T9CandidateRenderer.diff(previousState, next)
        delegate.setPreferAboveCursorAnchor(next.preferAboveCursorAnchor)
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
        val pinyinRowReady = if (patch.pinyin) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin") {
                delegate.renderPinyin(next.pinyinOptions, next.pinyinUseT9)
            }
        } else {
            true
        }
        if (patch.focus) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderFocus") {
                delegate.renderFocus()
            }
        }
        if (patch.visibility) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderVisibility") {
                if (next.shouldShow) {
                    delegate.showWhenPositioned(pinyinRowReady)
                } else {
                    delegate.hideCandidateUi()
                }
            }
        } else if (next.shouldShow && patch.pinyin && !pinyinRowReady) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderVisibility") {
                delegate.showWhenPositioned(false)
            }
        }
        previousState = next
    }
}

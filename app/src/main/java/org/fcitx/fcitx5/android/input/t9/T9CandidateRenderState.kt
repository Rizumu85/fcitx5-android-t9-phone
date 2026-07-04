/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation

data class T9CandidateRenderState(
    val panel: FcitxEvent.InputPanelEvent.Data,
    val candidates: FcitxEvent.PagedCandidateEvent.Data,
    val orientation: FloatingCandidatesOrientation,
    val showShortcutLabels: Boolean,
    val pinyinOptions: List<String>,
    val pinyinUseT9: Boolean,
    val focus: T9CandidateFocus,
    val preferAboveCursorAnchor: Boolean,
    val shouldShow: Boolean
) {
    val preeditSignature: String = buildPreeditSignature(panel)
    val candidateSignature: String = buildCandidateSignature(candidates, orientation, showShortcutLabels)
    val candidateContentSignature: String = buildCandidateContentSignature(candidates, orientation, showShortcutLabels)
    val pinyinSignature: String = buildPinyinSignature(pinyinOptions, pinyinUseT9)
    val visibilitySignature: String = "$shouldShow|$preferAboveCursorAnchor"

    companion object {
        private fun buildPreeditSignature(data: FcitxEvent.InputPanelEvent.Data): String =
            buildString {
                append(data.preedit).append('\n')
                append(data.auxUp).append('\n')
                append(data.auxDown)
            }

        private fun buildCandidateSignature(
            data: FcitxEvent.PagedCandidateEvent.Data,
            orientation: FloatingCandidatesOrientation,
            showShortcutLabels: Boolean
        ): String =
            buildString {
                append(buildCandidateContentSignature(data, orientation, showShortcutLabels))
                append("cursor=").append(data.cursorIndex)
            }

        private fun buildCandidateContentSignature(
            data: FcitxEvent.PagedCandidateEvent.Data,
            orientation: FloatingCandidatesOrientation,
            showShortcutLabels: Boolean
        ): String =
            buildString {
                append(orientation.name).append('|')
                append(showShortcutLabels).append('|')
                append(data.layoutHint.name).append('|')
                append(data.hasPrev).append('|').append(data.hasNext).append('\n')
                data.candidates.forEach {
                    append(it.label).append('|')
                    append(it.text).append('|')
                    append(it.comment).append('\n')
                }
            }

        private fun buildPinyinSignature(candidates: List<String>, useT9: Boolean): String =
            buildString {
                append(useT9).append('|')
                candidates.forEach {
                    append(it).append('\n')
                }
            }
    }
}

data class T9CandidateRenderPatch(
    val preedit: Boolean,
    val candidates: Boolean,
    val candidateContent: Boolean,
    val pinyin: Boolean,
    val focus: Boolean,
    val visibility: Boolean
)

object T9CandidateRenderer {
    fun diff(
        previous: T9CandidateRenderState?,
        next: T9CandidateRenderState
    ): T9CandidateRenderPatch {
        if (previous == null) {
            return T9CandidateRenderPatch(
                preedit = true,
                candidates = true,
                candidateContent = true,
                pinyin = true,
                focus = true,
                visibility = true
            )
        }
        val candidateContentChanged = previous.candidateContentSignature != next.candidateContentSignature
        return T9CandidateRenderPatch(
            preedit = previous.preeditSignature != next.preeditSignature,
            candidates = previous.candidateSignature != next.candidateSignature,
            candidateContent = candidateContentChanged,
            pinyin = previous.pinyinSignature != next.pinyinSignature || candidateContentChanged,
            focus = previous.focus != next.focus,
            visibility = previous.visibilitySignature != next.visibilitySignature
        )
    }
}

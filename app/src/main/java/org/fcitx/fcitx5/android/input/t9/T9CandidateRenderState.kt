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
    val shortcutStyle: T9ShortcutCandidateStyle,
    val reservePreeditRow: Boolean,
    val readingOptions: List<String>,
    val pinyinUseT9: Boolean,
    val focus: T9CandidateFocus,
    val preferAboveCursorAnchor: Boolean,
    val shouldShow: Boolean,
    val candidateStatus: T9CandidateStatus? = null
) {
    val preeditSnapshot: T9PreeditSnapshot by lazy(LazyThreadSafetyMode.NONE) {
        T9CandidateSnapshots.preedit(panel, reservePreeditRow)
    }
    val candidateSnapshot: T9CandidatePageSnapshot by lazy(LazyThreadSafetyMode.NONE) {
        T9CandidateSnapshots.renderCandidates(candidates, orientation, showShortcutLabels, shortcutStyle)
    }
    val pinyinSnapshot: T9PinyinSnapshot by lazy(LazyThreadSafetyMode.NONE) {
        T9CandidateSnapshots.pinyin(readingOptions, pinyinUseT9)
    }
    val visibilitySnapshot: T9VisibilitySnapshot by lazy(LazyThreadSafetyMode.NONE) {
        T9CandidateSnapshots.visibility(shouldShow, preferAboveCursorAnchor)
    }
}

enum class T9ShortcutCandidateStyle {
    ADAPTIVE_TAIL,
    UNIFORM_COMPACT
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
        val previousCandidate = previous.candidateSnapshot
        val nextCandidate = next.candidateSnapshot
        val candidateContentChanged =
            previousCandidate.contentSignature != nextCandidate.contentSignature ||
                previous.candidateStatus != next.candidateStatus
        val pinyinChanged = previous.pinyinSnapshot != next.pinyinSnapshot
        return T9CandidateRenderPatch(
            preedit = previous.preeditSnapshot != next.preeditSnapshot,
            candidates = candidateContentChanged ||
                previousCandidate.cursorSignature != nextCandidate.cursorSignature,
            candidateContent = candidateContentChanged,
            pinyin = pinyinChanged,
            focus = previous.focus != next.focus || pinyinChanged,
            visibility = previous.visibilitySnapshot != next.visibilitySnapshot
        )
    }
}

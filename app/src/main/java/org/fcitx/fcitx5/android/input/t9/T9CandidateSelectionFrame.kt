/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

data class T9CandidateSelectionFrame(
    val renderState: T9CandidateRenderState
)

object T9CandidateSelectionFramePlanner {
    fun plan(
        previous: T9CandidateRenderState,
        sourcePanel: FcitxEvent.InputPanelEvent.Data,
        candidates: FcitxEvent.PagedCandidateEvent.Data,
        presentation: T9PresentationState?
    ): T9CandidateSelectionFrame? {
        if (!previous.showShortcutLabels || previous.candidateStatus != null) return null
        val panel = presentation?.topReading?.let { topReading ->
            FcitxEvent.InputPanelEvent.Data(
                preedit = topReading,
                auxUp = sourcePanel.auxUp,
                auxDown = sourcePanel.auxDown
            )
        } ?: sourcePanel
        val next = previous.copy(
            panel = panel,
            candidates = candidates,
            reservePreeditRow = presentation?.reserveTopReadingRow == true,
            readingOptions = presentation?.readingOptions ?: emptyList(),
            candidateStatus = presentation?.candidateStatus
        )
        // Selection frames deliberately preserve candidate and reading-row geometry. Any source
        // change falls back to the complete snapshot publication rather than partially rebinding.
        if (previous.candidateSnapshot.contentSignature != next.candidateSnapshot.contentSignature) {
            return null
        }
        if (previous.pinyinSnapshot != next.pinyinSnapshot) return null
        if (previous.visibilitySnapshot != next.visibilitySnapshot) return null
        return T9CandidateSelectionFrame(next)
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation

object T9CandidateRenderStatePlanner {
    data class Input(
        val inputPanel: FcitxEvent.InputPanelEvent.Data,
        val candidates: FcitxEvent.PagedCandidateEvent.Data,
        val orientation: FloatingCandidatesOrientation,
        val usesSmartEnglish: Boolean,
        val usesPendingPunctuation: Boolean,
        val chineseT9Active: Boolean,
        val suppressEmptyCandidates: Boolean,
        val presentationState: T9PresentationState?,
        val focus: T9CandidateFocus,
        val usesHandwriting: Boolean = false
    )

    fun plan(input: Input): T9CandidateRenderState {
        val panel = if (input.suppressEmptyCandidates) {
            FcitxEvent.InputPanelEvent.Data()
        } else {
            input.presentationState?.topReading?.let {
                FcitxEvent.InputPanelEvent.Data(
                    it,
                    input.inputPanel.auxUp,
                    input.inputPanel.auxDown
                )
            } ?: input.inputPanel
        }
        val readingOptions = if (input.suppressEmptyCandidates) {
            emptyList()
        } else {
            input.presentationState?.readingOptions ?: emptyList()
        }
        return T9CandidateRenderState(
            panel = panel,
            candidates = input.candidates,
            orientation = input.orientation,
            showShortcutLabels = shouldShowShortcutLabels(input),
            // Punctuation choices are visually equivalent controls, so the final symbol must
            // not inherit the word candidate tail compaction rule.
            shortcutStyle = if (input.usesPendingPunctuation) {
                T9ShortcutCandidateStyle.UNIFORM_COMPACT
            } else {
                T9ShortcutCandidateStyle.ADAPTIVE_TAIL
            },
            candidateStatus = input.presentationState?.candidateStatus,
            reservePreeditRow = !input.suppressEmptyCandidates &&
                input.presentationState?.reserveTopReadingRow == true,
            readingOptions = readingOptions,
            pinyinUseT9 = input.chineseT9Active,
            focus = input.focus,
            // Product decision: Chinese T9 should use the same stable bubble placement as Smart
            // English. Anchoring above the cursor made Chinese composition feel like a different
            // UI surface and was called out as visually wrong after the layout-experiment revert.
            preferAboveCursorAnchor = false,
            shouldShow = shouldShow(input)
        )
    }

    private fun shouldShowShortcutLabels(input: Input): Boolean =
        input.candidates.candidates.isNotEmpty() &&
            input.presentationState?.candidateStatus == null &&
            (input.usesSmartEnglish || input.usesPendingPunctuation || input.chineseT9Active ||
                input.usesHandwriting)

    private fun shouldShow(input: Input): Boolean =
        !input.suppressEmptyCandidates && evaluateVisibility(
            inputPanel = input.inputPanel,
            topReading = input.presentationState?.topReading,
            readingRowVisible = input.presentationState?.readingRowVisible == true,
            hasVisibleCandidates = input.candidates.candidates.isNotEmpty(),
            hasCandidateStatus = input.presentationState?.candidateStatus != null
        )

    private fun evaluateVisibility(
        inputPanel: FcitxEvent.InputPanelEvent.Data,
        topReading: FormattedText?,
        readingRowVisible: Boolean,
        hasVisibleCandidates: Boolean,
        hasCandidateStatus: Boolean
    ): Boolean =
        inputPanel.preedit.isNotEmpty() ||
            hasVisibleCandidates ||
            inputPanel.auxUp.isNotEmpty() ||
            inputPanel.auxDown.isNotEmpty() ||
            topReading?.isNotEmpty() == true ||
            readingRowVisible ||
            hasCandidateStatus
}

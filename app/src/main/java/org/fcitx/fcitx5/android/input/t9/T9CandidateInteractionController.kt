/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9CandidateInteractionController(
    private val pipeline: T9CandidateUiSnapshotPipeline,
    private val host: Host
) {
    interface Host {
        fun setSmartEnglishCandidateIndex(originalIndex: Int): Boolean
        fun commitSmartEnglishCandidate(originalIndex: Int): Boolean
        fun commitPendingPunctuationCandidate(originalIndex: Int): Boolean
        fun previewPendingPunctuationCandidate(originalIndex: Int): Boolean
        fun refreshT9Ui()
        fun offsetEngineCandidatePage(delta: Int): Boolean
        fun selectChineseCandidate(
            originalIndex: Int,
            selectedCandidate: FcitxEvent.Candidate,
            matchedPrefix: String?,
            fromAllCandidates: Boolean,
            onSelected: (() -> Unit)? = null
        ): Boolean
    }

    fun commitSmartEnglishShortcut(shownIndex: Int): Boolean {
        val originalIndex = pipeline.smartEnglishShortcutOriginalIndex(shownIndex) ?: return false
        return host.commitSmartEnglishCandidate(originalIndex)
    }

    fun commitPendingPunctuationShortcut(shownIndex: Int): Boolean {
        val originalIndex = pipeline.pendingPunctuationShortcutOriginalIndex(shownIndex) ?: return false
        return host.commitPendingPunctuationCandidate(originalIndex)
    }

    fun moveBottomCandidate(delta: Int): Boolean? =
        when (val result = pipeline.moveCurrentBottomCandidate(delta)) {
            is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.SmartEnglish ->
                host.setSmartEnglishCandidateIndex(result.nextOriginalIndex)
            is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.PendingPunctuation -> {
                host.previewPendingPunctuationCandidate(result.previewOriginalIndex)
                host.refreshT9Ui()
                true
            }
            T9CandidateUiSnapshotPipeline.MoveBottomCandidate.Refresh -> {
                host.refreshT9Ui()
                true
            }
            is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.ChineseEngine ->
                host.offsetEngineCandidatePage(result.delta)
            null -> null
        }

    fun offsetBottomCandidatePage(delta: Int): Boolean? =
        when (val result = pipeline.offsetCurrentPage(delta)) {
            is T9CandidateUiSnapshotPipeline.PageOffset.SmartEnglish ->
                host.setSmartEnglishCandidateIndex(result.nextOriginalIndex)
            is T9CandidateUiSnapshotPipeline.PageOffset.PendingPunctuation -> {
                result.previewOriginalIndex?.let(host::previewPendingPunctuationCandidate)
                host.refreshT9Ui()
                true
            }
            T9CandidateUiSnapshotPipeline.PageOffset.Refresh -> {
                host.refreshT9Ui()
                true
            }
            is T9CandidateUiSnapshotPipeline.PageOffset.ChineseEngine ->
                host.offsetEngineCandidatePage(result.delta)
            null -> null
        }

    fun commitBottomCandidate(shownIndex: Int? = null): Boolean? {
        val result = if (shownIndex == null) {
            pipeline.commitCurrentBottomCandidate()
        } else {
            pipeline.commitBottomCandidateAt(shownIndex)
        }
        // Decision: keep owned candidate side effects behind one seam so view code does not
        // need to know which pipeline source produced the currently visible bottom row.
        return when (result) {
            is T9CandidateUiSnapshotPipeline.CommitBottomCandidate.SmartEnglish ->
                host.commitSmartEnglishCandidate(result.originalIndex)
            is T9CandidateUiSnapshotPipeline.CommitBottomCandidate.PendingPunctuation ->
                host.commitPendingPunctuationCandidate(result.originalIndex)
            is T9CandidateUiSnapshotPipeline.CommitBottomCandidate.Chinese ->
                host.selectChineseCandidate(
                    result.originalIndex,
                    result.candidate,
                    result.matchedPrefix,
                    result.fromAllCandidates
                )
            null -> null
        }
    }

    fun commitCurrentChineseCandidate(onSelected: () -> Unit): Boolean? {
        val result = pipeline.commitCurrentBottomCandidate()
            as? T9CandidateUiSnapshotPipeline.CommitBottomCandidate.Chinese
            ?: return null
        return host.selectChineseCandidate(
            result.originalIndex,
            result.candidate,
            result.matchedPrefix,
            result.fromAllCandidates,
            onSelected
        )
    }
}

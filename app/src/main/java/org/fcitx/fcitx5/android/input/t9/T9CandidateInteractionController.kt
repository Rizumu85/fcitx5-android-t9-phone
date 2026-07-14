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
        fun moveSmartEnglishSelection(originalIndex: Int): Boolean
        fun moveHandwritingSelection(originalIndex: Int): Boolean
        fun commitSmartEnglishCandidate(originalIndex: Int): Boolean
        fun commitHandwritingCandidate(originalIndex: Int): Boolean
        fun commitPendingPunctuationCandidate(originalIndex: Int): Boolean
        fun movePendingPunctuationSelection(originalIndex: Int): Boolean
        fun publishLocalSelection()
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
            is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.LocalSelection -> {
                val updated = when (result.source) {
                    T9CandidateUiSnapshotPipeline.ShownSource.SMART_ENGLISH ->
                        host.moveSmartEnglishSelection(result.originalIndex)
                    T9CandidateUiSnapshotPipeline.ShownSource.HANDWRITING ->
                        host.moveHandwritingSelection(result.originalIndex)
                    T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION ->
                        host.movePendingPunctuationSelection(result.originalIndex)
                    T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_BULK,
                    T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_LOCAL,
                    T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_ENGINE -> true
                    T9CandidateUiSnapshotPipeline.ShownSource.OTHER -> false
                }
                if (!updated) return false
                host.publishLocalSelection()
                true
            }
            is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.PageTransition ->
                applyPageOffset(result.offset)
            null -> null
        }

    fun offsetBottomCandidatePage(delta: Int): Boolean? =
        pipeline.offsetCurrentPage(delta)?.let(::applyPageOffset)

    private fun applyPageOffset(result: T9CandidateUiSnapshotPipeline.PageOffset): Boolean =
        when (result) {
            is T9CandidateUiSnapshotPipeline.PageOffset.SmartEnglish -> {
                if (!host.moveSmartEnglishSelection(result.nextOriginalIndex)) return false
                host.refreshT9Ui()
                true
            }
            is T9CandidateUiSnapshotPipeline.PageOffset.PendingPunctuation -> {
                val preview = result.previewOriginalIndex
                if (preview != null && !host.movePendingPunctuationSelection(preview)) return false
                host.refreshT9Ui()
                true
            }
            is T9CandidateUiSnapshotPipeline.PageOffset.Handwriting -> {
                if (!host.moveHandwritingSelection(result.nextOriginalIndex)) return false
                host.refreshT9Ui()
                true
            }
            T9CandidateUiSnapshotPipeline.PageOffset.Refresh -> {
                host.refreshT9Ui()
                true
            }
            is T9CandidateUiSnapshotPipeline.PageOffset.ChineseEngine ->
                host.offsetEngineCandidatePage(result.delta)
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
            is T9CandidateUiSnapshotPipeline.CommitBottomCandidate.Handwriting ->
                host.commitHandwritingCandidate(result.originalIndex)
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

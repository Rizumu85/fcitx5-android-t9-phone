/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class PhysicalDeleteCoordinator(
    private val captureEditor: () -> EditorSnapshot?
) {
    data class Selection(
        val start: Int,
        val end: Int
    ) {
        val isSelected: Boolean
            get() = start != end

        val normalizedStart: Int
            get() = minOf(start, end)

        val normalizedEnd: Int
            get() = maxOf(start, end)

        val isValid: Boolean
            get() = start >= 0 && end >= 0
    }

    data class EditorSnapshot(
        val trackedSelection: Selection,
        val extractedTextEmpty: Boolean?,
        val extractedSelection: Selection?,
        val hasTextBeforeCursor: Boolean?,
        val hasTextAfterCursor: Boolean?
    )

    data class InputState(
        val hasComposingText: Boolean,
        val hasT9Composition: Boolean,
        val hasPendingMultiTap: Boolean,
        val hasPendingPunctuation: Boolean,
        val canReopenResolvedSegment: Boolean
    ) {
        val isIdle: Boolean
            get() = !hasComposingText &&
                !hasT9Composition &&
                !hasPendingMultiTap &&
                !hasPendingPunctuation
    }

    enum class DeleteKind {
        SELECTION,
        BEFORE_CURSOR
    }

    data class DeletePlan(
        val kind: DeleteKind,
        val predictedCursor: Int?
    )

    sealed class Decision {
        object HideIme : Decision()
        object ReopenResolvedSegment : Decision()
        data class Delete(val plan: DeletePlan) : Decision()
    }

    fun decide(input: InputState, repeatCount: Int): Decision? {
        if (input.canReopenResolvedSegment) return Decision.ReopenResolvedSegment
        if (!input.isIdle) return null
        val editor = captureEditor() ?: return null
        if (repeatCount == 0 && editor.isKnownEmpty()) return Decision.HideIme
        return editor.deletePlan()?.let(Decision::Delete)
    }

    fun planPasswordDelete(): DeletePlan? =
        captureEditor()?.deletePlan()

    private fun EditorSnapshot.isKnownEmpty(): Boolean {
        val tracked = trackedSelection
        if (tracked.isSelected || tracked.start > 0 || tracked.end > 0) return false
        return extractedTextEmpty
            ?: (hasTextBeforeCursor == false && hasTextAfterCursor == false)
    }

    private fun EditorSnapshot.deletePlan(): DeletePlan? {
        val tracked = trackedSelection
        if (tracked.isSelected) {
            return DeletePlan(DeleteKind.SELECTION, tracked.normalizedStart)
        }
        extractedSelection?.takeIf(Selection::isValid)?.let { extracted ->
            if (extracted.isSelected) {
                return DeletePlan(DeleteKind.SELECTION, extracted.normalizedStart)
            }
            if (extracted.start > 0) {
                val predictedCursor = if (tracked.start > 0) {
                    tracked.start - 1
                } else {
                    extracted.start - 1
                }
                return DeletePlan(DeleteKind.BEFORE_CURSOR, predictedCursor)
            }
            if (extractedTextEmpty == false) return null
        }
        if (tracked.start <= 0 && hasTextBeforeCursor != true) return null
        return DeletePlan(
            kind = DeleteKind.BEFORE_CURSOR,
            predictedCursor = tracked.start.takeIf { it > 0 }?.minus(1)
        )
    }
}

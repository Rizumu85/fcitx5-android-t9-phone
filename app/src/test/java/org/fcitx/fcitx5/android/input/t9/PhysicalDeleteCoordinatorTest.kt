/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalDeleteCoordinatorTest {

    @Test
    fun resolvedSegmentWinsWithoutReadingEditor() {
        var captureCount = 0
        val coordinator = PhysicalDeleteCoordinator {
            captureCount += 1
            editor(extractedTextEmpty = true)
        }

        val decision = coordinator.decide(
            input = idleState().copy(canReopenResolvedSegment = true),
            repeatCount = 0
        )

        assertEquals(PhysicalDeleteCoordinator.Decision.ReopenResolvedSegment, decision)
        assertEquals(0, captureCount)
    }

    @Test
    fun activeCompositionFallsThroughWithoutReadingEditor() {
        var captureCount = 0
        val coordinator = PhysicalDeleteCoordinator {
            captureCount += 1
            editor(extractedTextEmpty = true)
        }

        val decision = coordinator.decide(
            input = idleState().copy(hasPendingPunctuation = true),
            repeatCount = 0
        )

        assertNull(decision)
        assertEquals(0, captureCount)
    }

    @Test
    fun initialDeleteOnKnownEmptyEditorHidesImeFromOneSnapshot() {
        var captureCount = 0
        val coordinator = PhysicalDeleteCoordinator {
            captureCount += 1
            editor(extractedTextEmpty = true)
        }

        val decision = coordinator.decide(idleState(), repeatCount = 0)

        assertEquals(PhysicalDeleteCoordinator.Decision.HideIme, decision)
        assertEquals(1, captureCount)
    }

    @Test
    fun nonEmptyEditorPlansDeleteFromTheSameSnapshot() {
        var captureCount = 0
        val coordinator = PhysicalDeleteCoordinator {
            captureCount += 1
            editor(
                extractedTextEmpty = false,
                extractedSelection = PhysicalDeleteCoordinator.Selection(3, 3)
            )
        }

        val decision = coordinator.decide(idleState(), repeatCount = 0)

        assertEquals(
            PhysicalDeleteCoordinator.Decision.Delete(
                PhysicalDeleteCoordinator.DeletePlan(
                    kind = PhysicalDeleteCoordinator.DeleteKind.BEFORE_CURSOR,
                    predictedCursor = 2
                )
            ),
            decision
        )
        assertEquals(1, captureCount)
    }

    @Test
    fun trackedSelectionPlansSelectionDeleteWithoutCursorGuessing() {
        val coordinator = PhysicalDeleteCoordinator {
            editor(tracked = PhysicalDeleteCoordinator.Selection(2, 5))
        }

        val plan = coordinator.planPasswordDelete()

        assertEquals(
            PhysicalDeleteCoordinator.DeletePlan(
                kind = PhysicalDeleteCoordinator.DeleteKind.SELECTION,
                predictedCursor = 2
            ),
            plan
        )
    }

    @Test
    fun repeatOnEmptyEditorDoesNotHideImeOrInventADelete() {
        val coordinator = PhysicalDeleteCoordinator {
            editor(extractedTextEmpty = true)
        }

        assertNull(coordinator.decide(idleState(), repeatCount = 1))
    }

    @Test
    fun fallbackSurroundingTextCanProveEmptyEditor() {
        val coordinator = PhysicalDeleteCoordinator {
            editor(
                extractedTextEmpty = null,
                hasTextBeforeCursor = false,
                hasTextAfterCursor = false
            )
        }

        assertEquals(
            PhysicalDeleteCoordinator.Decision.HideIme,
            coordinator.decide(idleState(), repeatCount = 0)
        )
    }

    private fun idleState() = PhysicalDeleteCoordinator.InputState(
        hasComposingText = false,
        hasT9Composition = false,
        hasPendingMultiTap = false,
        hasPendingPunctuation = false,
        canReopenResolvedSegment = false
    )

    private fun editor(
        tracked: PhysicalDeleteCoordinator.Selection = PhysicalDeleteCoordinator.Selection(0, 0),
        extractedTextEmpty: Boolean? = null,
        extractedSelection: PhysicalDeleteCoordinator.Selection? = null,
        hasTextBeforeCursor: Boolean? = null,
        hasTextAfterCursor: Boolean? = null
    ) = PhysicalDeleteCoordinator.EditorSnapshot(
        trackedSelection = tracked,
        extractedTextEmpty = extractedTextEmpty,
        extractedSelection = extractedSelection,
        hasTextBeforeCursor = hasTextBeforeCursor,
        hasTextAfterCursor = hasTextAfterCursor
    )
}

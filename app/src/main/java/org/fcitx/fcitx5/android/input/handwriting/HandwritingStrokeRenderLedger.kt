/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

/** Reconciles AndroidX Ink completion callbacks with coordinator-owned stroke history. */
internal class HandwritingStrokeRenderLedger<Id, Stroke> {
    private val expectedStrokeIds = mutableListOf<Id>()
    private val finishedStrokes = mutableMapOf<Id, Stroke>()
    private var desiredStrokeCount = 0

    fun expectLocalStrokeCompletion(id: Id) {
        // Ink may report completion synchronously. Reserve the coordinator slot before calling it
        // so the just-finished stroke cannot be mistaken for stale geometry and removed.
        if (id !in expectedStrokeIds) expectedStrokeIds += id
        desiredStrokeCount++
    }

    fun acceptFinished(strokes: Map<Id, Stroke>): List<Stroke> {
        strokes.forEach { (id, stroke) ->
            // Listener maps do not promise completion order. Render in the order captured at
            // finishStroke() so undo always removes the user's most recent stroke.
            if (id in expectedStrokeIds) finishedStrokes.putIfAbsent(id, stroke)
        }
        trimToDesiredCount()
        return renderedStrokes()
    }

    fun reconcileCoordinatorCount(count: Int): List<Stroke> {
        desiredStrokeCount = count.coerceAtLeast(0)
        trimToDesiredCount()
        return renderedStrokes()
    }

    private fun trimToDesiredCount() {
        if (expectedStrokeIds.size <= desiredStrokeCount) return
        val removedIds = expectedStrokeIds.subList(desiredStrokeCount, expectedStrokeIds.size)
            .toList()
        removedIds.forEach(finishedStrokes::remove)
        expectedStrokeIds.subList(desiredStrokeCount, expectedStrokeIds.size).clear()
    }

    private fun renderedStrokes() = expectedStrokeIds.mapNotNull(finishedStrokes::get)
}

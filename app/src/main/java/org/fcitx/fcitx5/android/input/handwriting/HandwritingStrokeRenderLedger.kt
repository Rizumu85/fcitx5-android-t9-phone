/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

/** Reconciles AndroidX Ink completion callbacks with coordinator-owned stroke history. */
internal class HandwritingStrokeRenderLedger<T> {
    private val finishedIds = mutableListOf<T>()
    private var desiredStrokeCount = 0

    fun expectLocalStrokeCompletion() {
        // Ink may report completion synchronously. Reserve the coordinator slot before calling it
        // so the just-finished stroke cannot be mistaken for stale geometry and removed.
        desiredStrokeCount++
    }

    fun acceptFinished(ids: Iterable<T>): Set<T> {
        ids.forEach { id ->
            if (id !in finishedIds) finishedIds += id
        }
        return trimToDesiredCount()
    }

    fun reconcileCoordinatorCount(count: Int): Set<T> {
        desiredStrokeCount = count.coerceAtLeast(0)
        return trimToDesiredCount()
    }

    private fun trimToDesiredCount(): Set<T> {
        if (finishedIds.size <= desiredStrokeCount) return emptySet()
        return finishedIds
            .subList(desiredStrokeCount, finishedIds.size)
            .toSet()
            .also { finishedIds.subList(desiredStrokeCount, finishedIds.size).clear() }
    }
}

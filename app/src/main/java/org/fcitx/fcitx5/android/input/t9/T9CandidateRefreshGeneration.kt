/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class T9CandidateRefreshGeneration(
    private val postRefresh: (() -> Unit) -> Unit,
    private val refreshNow: (Generation) -> Unit
) {
    data class Generation(
        val id: Long,
        val traceInputId: Long?
    )

    private var nextId = 1L
    private var pending: Generation? = null

    fun requestRefresh(traceInputId: Long?): Generation {
        pending?.takeIf { it.traceInputId == traceInputId }?.let { return it }
        val generation = newGeneration(traceInputId)
        pending = generation
        postRefresh {
            if (pending?.id != generation.id) return@postRefresh
            pending = null
            refreshNow(generation)
        }
        return generation
    }

    fun publishReady(traceInputId: Long?): Generation {
        val generation = pending
            ?.takeIf { it.traceInputId == traceInputId }
            ?: newGeneration(traceInputId)
        // A gate-accepted source pair is already complete on the main thread. Consuming the
        // generation here makes its queued callback stale instead of paying another queue turn.
        pending = null
        refreshNow(generation)
        return generation
    }

    fun cancel() {
        pending = null
    }

    private fun newGeneration(traceInputId: Long?): Generation =
        Generation(nextId++, traceInputId)
}

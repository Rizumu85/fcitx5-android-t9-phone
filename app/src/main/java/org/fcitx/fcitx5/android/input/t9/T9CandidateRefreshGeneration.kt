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
        val generation = Generation(nextId++, traceInputId)
        pending = generation
        postRefresh {
            if (pending?.id != generation.id) return@postRefresh
            pending = null
            refreshNow(generation)
        }
        return generation
    }

    fun cancel() {
        pending = null
    }
}

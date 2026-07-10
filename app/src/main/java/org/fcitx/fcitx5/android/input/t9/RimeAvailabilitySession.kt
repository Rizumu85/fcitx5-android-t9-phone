/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class RimeAvailabilitySession(
    initial: FcitxEvent.RimeAvailabilityEvent.Data =
        FcitxEvent.RimeAvailabilityEvent.Data.Unavailable
) {
    data class Snapshot(
        val state: FcitxEvent.RimeAvailabilityEvent.State,
        val activeSchema: String,
        val generation: Long
    ) {
        val isReady: Boolean
            get() = state == FcitxEvent.RimeAvailabilityEvent.State.Ready
    }

    data class Transition(
        val previous: Snapshot,
        val current: Snapshot
    ) {
        val changed: Boolean
            get() = previous != current
        val becameReady: Boolean
            get() = !previous.isReady && current.isReady
        val leftReady: Boolean
            get() = previous.isReady && !current.isReady
        val failed: Boolean
            get() = current.state == FcitxEvent.RimeAvailabilityEvent.State.Failed ||
                current.state == FcitxEvent.RimeAvailabilityEvent.State.Unavailable
    }

    var current = Snapshot(
        state = initial.state,
        activeSchema = initial.activeSchema.trim(),
        generation = 0L
    )
        private set

    fun update(data: FcitxEvent.RimeAvailabilityEvent.Data): Transition {
        val previous = current
        val schema = data.activeSchema.trim().ifEmpty { previous.activeSchema }
        if (previous.state == data.state && previous.activeSchema == schema) {
            return Transition(previous, previous)
        }
        current = Snapshot(
            state = data.state,
            activeSchema = schema,
            generation = previous.generation + 1L
        )
        return Transition(previous, current)
    }

    fun observeActiveSchema(schema: String): Transition {
        val previous = current
        val normalized = schema.trim()
        if (normalized.isEmpty() || normalized == previous.activeSchema) {
            return Transition(previous, previous)
        }
        current = previous.copy(
            activeSchema = normalized,
            generation = previous.generation + 1L
        )
        return Transition(previous, current)
    }
}

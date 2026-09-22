/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core

data class FcitxPresentationOrigin(val commandId: Long = 0, val frameId: Long = 0)

data class FcitxPresentationFrame(
    val origin: FcitxPresentationOrigin,
    val inputPanel: FcitxEvent.InputPanelEvent.Data,
    val candidates: FcitxEvent.PagedCandidateEvent.Data
)

/** Confined to the Fcitx dispatcher; origin is captured before events cross to Android. */
class FcitxPresentationSequencer {
    var commandId: Long = 0
    private var nextFrame = 0L
    private var panel: FcitxEvent.InputPanelEvent? = null
    var completeFrame: FcitxPresentationFrame? = null
        private set

    fun stamp(event: FcitxEvent<*>): FcitxEvent<*> = when (event) {
        is FcitxEvent.InputPanelEvent -> event.copy(
            origin = FcitxPresentationOrigin(commandId, ++nextFrame)
        ).also { panel = it }
        is FcitxEvent.PagedCandidateEvent -> {
            val input = panel
            panel = null
            if (input == null) event else event.copy(origin = input.origin).also {
                // AndroidFrontend emits these two callbacks together in one native UI flush.
                completeFrame = FcitxPresentationFrame(input.origin, input.data, event.data)
            }
        }
        else -> event
    }

    fun reset() {
        commandId = 0
        panel = null
        completeFrame = null
    }
}

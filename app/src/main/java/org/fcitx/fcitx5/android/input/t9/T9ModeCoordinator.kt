/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class T9ModeCoordinator(
    initialMode: T9InputMode = T9InputMode.CHINESE,
    private val beforeModeChange: () -> Unit,
    private val onEnglishModeEntered: () -> Unit,
    private val onModeLabelChanged: (String) -> Unit,
    private val showModeIndicator: (String) -> Unit
) {
    var current: T9InputMode = initialMode
        private set

    val currentLabel: String
        get() = current.label

    fun switchToNextMode() {
        // Mode switches must clear composition before observers see the new label, otherwise
        // stale Smart English digits can leak into the first frame of the next mode.
        beforeModeChange()
        current = current.next()
        if (current == T9InputMode.ENGLISH) {
            onEnglishModeEntered()
        }
        val label = currentLabel
        onModeLabelChanged(label)
        showModeIndicator(label)
    }
}

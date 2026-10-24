/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.utils

import android.os.Build
import android.view.inputmethod.InputConnection

fun InputConnection.withBatchEdit(block: InputConnection.() -> Unit) {
    beginBatchEdit()
    block.invoke(this)
    endBatchEdit()
}

/**
 * @param immediate also deliver the current anchor now, for a surface that was just recreated and
 * would otherwise wait for the editor's next cursor change.
 */
fun InputConnection.monitorCursorAnchor(enable: Boolean = true, immediate: Boolean = false): Boolean {
    if (!enable) {
        requestCursorUpdates(0)
        return false
    }
    val mode = InputConnection.CURSOR_UPDATE_MONITOR or
        if (immediate) InputConnection.CURSOR_UPDATE_IMMEDIATE else 0
    var scheduled = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        scheduled = requestCursorUpdates(
            mode,
            InputConnection.CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS or InputConnection.CURSOR_UPDATE_FILTER_INSERTION_MARKER
        )
    }
    if (!scheduled) {
        scheduled = requestCursorUpdates(mode)
    }
    return scheduled
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class T9UiRefreshScheduler(
    private val postRefresh: (() -> Unit) -> Unit,
    private val refreshNow: () -> Unit
) {
    private var pending = false

    fun requestRefresh() {
        if (pending) return
        pending = true
        postRefresh {
            if (!pending) return@postRefresh
            pending = false
            refreshNow()
        }
    }

    fun refreshImmediately() {
        pending = false
        refreshNow()
    }

    fun cancel() {
        pending = false
    }
}

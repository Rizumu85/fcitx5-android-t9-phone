/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9CandidateVisibilityPlanner {
    data class Request(
        val shouldShow: Boolean,
        val contentReady: Boolean,
        val preferAboveInputPanel: Boolean
    )

    enum class Action {
        NONE,
        SHOW,
        HIDE
    }

    fun plan(previous: Request?, next: Request): Action {
        if (previous == null) {
            return if (next.shouldShow) Action.SHOW else Action.HIDE
        }
        if (next.shouldShow != previous.shouldShow) {
            return if (next.shouldShow) Action.SHOW else Action.HIDE
        }
        if (!next.shouldShow) return Action.NONE
        if (!previous.contentReady && next.contentReady) return Action.SHOW
        if (next.preferAboveInputPanel != previous.preferAboveInputPanel) return Action.SHOW
        return Action.NONE
    }
}

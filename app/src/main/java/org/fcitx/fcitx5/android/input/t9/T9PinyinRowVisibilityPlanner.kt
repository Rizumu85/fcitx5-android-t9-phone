/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9PinyinRowVisibilityPlanner {
    enum class Visibility {
        GONE,
        INVISIBLE,
        VISIBLE
    }

    data class Snapshot(
        val targetVisible: Boolean,
        val wrapperVisibility: Visibility,
        val barVisibility: Visibility
    )

    enum class SetVisibleAction {
        NOOP_READY,
        HIDE_NOW,
        SHOW_NOW,
        WAIT_FOR_WIDTH
    }

    fun planSetVisible(
        requestedVisible: Boolean,
        snapshot: Snapshot,
        widthReady: Boolean
    ): SetVisibleAction {
        if (!requestedVisible &&
            !snapshot.targetVisible &&
            snapshot.wrapperVisibility == Visibility.GONE &&
            snapshot.barVisibility == Visibility.GONE
        ) {
            return SetVisibleAction.NOOP_READY
        }
        if (requestedVisible &&
            snapshot.wrapperVisibility == Visibility.VISIBLE &&
            snapshot.barVisibility == Visibility.VISIBLE
        ) {
            return SetVisibleAction.NOOP_READY
        }
        if (!requestedVisible) {
            return SetVisibleAction.HIDE_NOW
        }
        if (!widthReady) return SetVisibleAction.WAIT_FOR_WIDTH
        // The Canvas strip owns complete item geometry before this decision. Once the shared row
        // width is ready, delaying another layout turn only postpones the atomic surface reveal.
        return SetVisibleAction.SHOW_NOW
    }

    enum class DeferredWidthAction {
        IGNORE,
        SHOW_NOW,
        KEEP_WAITING
    }

    fun planDeferredWidth(
        targetVisible: Boolean,
        widthReady: Boolean
    ): DeferredWidthAction =
        when {
            !targetVisible -> DeferredWidthAction.IGNORE
            widthReady -> DeferredWidthAction.SHOW_NOW
            else -> DeferredWidthAction.KEEP_WAITING
        }

}

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
        SYNC_VISIBLE_LAYOUT,
        WAIT_FOR_LAYOUT,
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
            return SetVisibleAction.SYNC_VISIBLE_LAYOUT
        }
        if (!requestedVisible) {
            return SetVisibleAction.HIDE_NOW
        }
        if (!widthReady) return SetVisibleAction.WAIT_FOR_WIDTH
        // Product decision: a freshly appearing pinyin row must not draw until its chip views have
        // received one layout pass. Width readiness alone still allowed a one-frame clipped row.
        return SetVisibleAction.WAIT_FOR_LAYOUT
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

    enum class LayoutPassAction {
        NONE,
        APPLY_WIDTH,
        SHOW_WAITING_ROW
    }

    fun planLayoutPass(
        targetVisible: Boolean,
        wrapperVisibility: Visibility,
        rowVisible: Boolean,
        widthChanged: Boolean,
        widthReady: Boolean
    ): LayoutPassAction {
        if (rowVisible) return LayoutPassAction.NONE
        if (!widthReady) return LayoutPassAction.NONE
        // Product decision: a row waiting invisibly for the first trusted Hanzi measurement should
        // become visible in the same layout pass that validates its width, avoiding a clipped first frame.
        if (targetVisible && wrapperVisibility == Visibility.INVISIBLE) {
            return LayoutPassAction.SHOW_WAITING_ROW
        }
        return if (widthChanged) {
            LayoutPassAction.APPLY_WIDTH
        } else {
            LayoutPassAction.NONE
        }
    }
}

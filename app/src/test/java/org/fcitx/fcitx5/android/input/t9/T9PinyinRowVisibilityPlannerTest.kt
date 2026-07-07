/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.input.t9.T9PinyinRowVisibilityPlanner.DeferredWidthAction
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowVisibilityPlanner.LayoutPassAction
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowVisibilityPlanner.SetVisibleAction
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowVisibilityPlanner.Visibility
import org.junit.Assert.assertEquals
import org.junit.Test

class T9PinyinRowVisibilityPlannerTest {
    @Test
    fun hiddenRowCanStayHiddenWithoutWork() {
        val action = T9PinyinRowVisibilityPlanner.planSetVisible(
            requestedVisible = false,
            snapshot = snapshot(
                targetVisible = false,
                wrapper = Visibility.GONE,
                bar = Visibility.GONE
            ),
            widthReady = false
        )

        assertEquals(SetVisibleAction.NOOP_READY, action)
    }

    @Test
    fun visibleRowTreatsRepeatedShowAsNoOp() {
        val action = T9PinyinRowVisibilityPlanner.planSetVisible(
            requestedVisible = true,
            snapshot = snapshot(
                targetVisible = true,
                wrapper = Visibility.VISIBLE,
                bar = Visibility.VISIBLE
            ),
            widthReady = true
        )

        assertEquals(SetVisibleAction.NOOP_READY, action)
    }

    @Test
    fun showRequestWaitsWhenWidthIsNotReady() {
        val action = T9PinyinRowVisibilityPlanner.planSetVisible(
            requestedVisible = true,
            snapshot = snapshot(targetVisible = false),
            widthReady = false
        )

        assertEquals(SetVisibleAction.WAIT_FOR_WIDTH, action)
    }

    @Test
    fun freshShowRequestWaitsForOneLayoutEvenWhenWidthIsReady() {
        val action = T9PinyinRowVisibilityPlanner.planSetVisible(
            requestedVisible = true,
            snapshot = snapshot(targetVisible = false),
            widthReady = true
        )

        assertEquals(SetVisibleAction.WAIT_FOR_LAYOUT, action)
    }

    @Test
    fun deferredWidthIgnoresStaleHiddenTarget() {
        assertEquals(
            DeferredWidthAction.IGNORE,
            T9PinyinRowVisibilityPlanner.planDeferredWidth(
                targetVisible = false,
                widthReady = true
            )
        )
    }

    @Test
    fun layoutPassShowsInvisibleWaitingRowWhenWidthIsReady() {
        val action = T9PinyinRowVisibilityPlanner.planLayoutPass(
            targetVisible = true,
            wrapperVisibility = Visibility.INVISIBLE,
            rowVisible = false,
            widthChanged = true,
            widthReady = true
        )

        assertEquals(LayoutPassAction.SHOW_WAITING_ROW, action)
    }

    @Test
    fun layoutPassAppliesWidthForHiddenPreparedRow() {
        val action = T9PinyinRowVisibilityPlanner.planLayoutPass(
            targetVisible = false,
            wrapperVisibility = Visibility.GONE,
            rowVisible = false,
            widthChanged = true,
            widthReady = true
        )

        assertEquals(LayoutPassAction.APPLY_WIDTH, action)
    }

    private fun snapshot(
        targetVisible: Boolean,
        wrapper: Visibility = Visibility.GONE,
        bar: Visibility = Visibility.GONE
    ): T9PinyinRowVisibilityPlanner.Snapshot =
        T9PinyinRowVisibilityPlanner.Snapshot(
            targetVisible = targetVisible,
            wrapperVisibility = wrapper,
            barVisibility = bar
        )
}

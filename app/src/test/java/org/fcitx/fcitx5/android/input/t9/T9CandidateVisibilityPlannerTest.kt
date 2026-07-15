/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9CandidateVisibilityPlannerTest {

    @Test
    fun firstVisibleFrameRequestsShow() {
        assertEquals(
            T9CandidateVisibilityPlanner.Action.SHOW,
            T9CandidateVisibilityPlanner.plan(
                previous = null,
                next = request(shouldShow = true)
            )
        )
    }

    @Test
    fun hiddenStateDoesNotHideRepeatedly() {
        assertEquals(
            T9CandidateVisibilityPlanner.Action.NONE,
            T9CandidateVisibilityPlanner.plan(
                previous = request(shouldShow = false),
                next = request(shouldShow = false)
            )
        )
    }

    @Test
    fun pendingVisibleFrameShowsAgainWhenContentBecomesReady() {
        assertEquals(
            T9CandidateVisibilityPlanner.Action.SHOW,
            T9CandidateVisibilityPlanner.plan(
                previous = request(shouldShow = true, contentReady = false),
                next = request(shouldShow = true, contentReady = true)
            )
        )
    }

    @Test
    fun stableVisibleFrameDoesNotRequestLayoutAgain() {
        assertEquals(
            T9CandidateVisibilityPlanner.Action.NONE,
            T9CandidateVisibilityPlanner.plan(
                previous = request(shouldShow = true, contentReady = true),
                next = request(shouldShow = true, contentReady = true)
            )
        )
    }

    @Test
    fun anchorPreferenceChangeRequestsShowReposition() {
        assertEquals(
            T9CandidateVisibilityPlanner.Action.SHOW,
            T9CandidateVisibilityPlanner.plan(
                previous = request(shouldShow = true, preferAboveInputPanel = false),
                next = request(shouldShow = true, preferAboveInputPanel = true)
            )
        )
    }

    private fun request(
        shouldShow: Boolean,
        contentReady: Boolean = true,
        preferAboveInputPanel: Boolean = false
    ): T9CandidateVisibilityPlanner.Request =
        T9CandidateVisibilityPlanner.Request(
            shouldShow = shouldShow,
            contentReady = contentReady,
            preferAboveInputPanel = preferAboveInputPanel
        )
}

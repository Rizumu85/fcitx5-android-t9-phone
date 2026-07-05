/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9PinyinChipScrollPlannerTest {
    @Test
    fun hiddenTrailingChipScrollsOnlyUntilItIsVisible() {
        val target = T9PinyinChipScrollPlanner.targetScrollX(
            currentScrollX = 0,
            viewportWidthPx = 150,
            itemStartPx = 140,
            itemEndPx = 164
        )

        assertEquals(14, target)
    }

    @Test
    fun visibleChipKeepsCurrentScroll() {
        val target = T9PinyinChipScrollPlanner.targetScrollX(
            currentScrollX = 20,
            viewportWidthPx = 150,
            itemStartPx = 80,
            itemEndPx = 120
        )

        assertEquals(20, target)
    }

    @Test
    fun leadingHiddenChipScrollsBackToItsStart() {
        val target = T9PinyinChipScrollPlanner.targetScrollX(
            currentScrollX = 90,
            viewportWidthPx = 150,
            itemStartPx = 64,
            itemEndPx = 88
        )

        assertEquals(64, target)
    }
}

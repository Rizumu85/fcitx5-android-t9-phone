/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9ShortcutTailPolicyTest {
    @Test
    fun onlyTheLastVisibleCandidateEdgeAlignsToTheBubbleTail() {
        assertTrue(
            T9ShortcutTailPolicy.edgeAlignsCandidateToBubbleTail(
                isCandidate = true,
                isLastVisibleItem = true
            )
        )
        assertFalse(
            T9ShortcutTailPolicy.edgeAlignsCandidateToBubbleTail(
                isCandidate = true,
                isLastVisibleItem = false
            )
        )
        assertFalse(
            T9ShortcutTailPolicy.edgeAlignsCandidateToBubbleTail(
                isCandidate = false,
                isLastVisibleItem = true
            )
        )
        assertFalse(
            T9ShortcutTailPolicy.edgeAlignsCandidateToBubbleTail(
                isCandidate = true,
                isLastVisibleItem = true,
                preserveUniformMinimumWidth = true
            )
        )
    }

}

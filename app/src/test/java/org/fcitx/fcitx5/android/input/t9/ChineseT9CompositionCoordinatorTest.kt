/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9CompositionCoordinatorTest {

    @Test
    fun snapshotKeepsForwardedDigitsAuthoritativeOverConvertedPreedit() {
        val coordinator = coordinator()

        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_4)
        val snapshot = coordinator.snapshot("g")

        assertEquals("4", snapshot.rawSequence)
        assertEquals("4", snapshot.currentSegment)
        assertEquals(1, snapshot.keyCount)
    }

    @Test
    fun selectedPinyinPrefixCanBeMatchedAndConsumedThroughOneInterface() {
        val coordinator = coordinator()
        coordinator.handleForwardedKeyDown(KeyEvent.KEYCODE_4)
        coordinator.snapshot("g")

        val request = coordinator.selectPinyin("g")
        val candidate = FcitxEvent.Candidate(label = "", text = "个", comment = "g")

        assertTrue(request != null)
        assertTrue(coordinator.candidateMatchesResolvedPrefix(candidate, "g"))
        assertEquals("", coordinator.consumeResolvedPrefix("g"))
        assertFalse(coordinator.hasState())
    }

    private fun coordinator() = ChineseT9CompositionCoordinator(
        formatText = ::formatted,
        buildRawPreeditDisplay = ::formatted
    )

    private fun formatted(text: String): FormattedText? =
        text.takeIf { it.isNotEmpty() }?.let {
            FormattedText(
                strings = arrayOf(it),
                flags = intArrayOf(TextFormatFlag.NoFlag.flag),
                cursor = -1
            )
        }
}

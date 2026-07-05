/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEnglishT9CoordinatorTest {

    @Test
    fun commitCandidateClearsPendingDigitBehindCoordinatorSeam() {
        val host = FakeHost()
        val coordinator = host.coordinator()

        coordinator.appendDigit(2)

        assertTrue(coordinator.commitCandidate())
        assertEquals(listOf("a "), host.committedTexts)
        assertEquals(1, host.resetPendingDigitCount)
    }

    @Test
    fun enabledChangeResetsSessionAndPendingDigit() {
        val host = FakeHost()
        val coordinator = host.coordinator()

        coordinator.appendDigit(2)
        coordinator.onEnabledChanged(false)

        assertEquals(1, host.resetPendingDigitCount)
        assertEquals(null, coordinator.paged())
    }

    private class FakeHost {
        val committedTexts = mutableListOf<String>()
        var resetPendingDigitCount = 0
        var refreshCount = 0

        fun coordinator(): SmartEnglishT9Coordinator {
            val controller = SmartEnglishT9Controller(
                candidateProvider = { digits, limit ->
                    mapOf("2" to listOf("a"))[digits].orEmpty().take(limit)
                },
                candidateLimit = 10,
                noMatchText = "No match",
                isActive = { true },
                shouldLearnWords = { false },
                commitText = { committedTexts += it },
                refreshUi = { refreshCount += 1 }
            )
            return SmartEnglishT9Coordinator(
                controller = controller,
                scope = CoroutineScope(Dispatchers.Unconfined),
                ioDispatcher = Dispatchers.Unconfined,
                mainDispatcher = Dispatchers.Unconfined,
                isEnabled = { true },
                isActive = { true },
                resetPendingDigit = { resetPendingDigitCount += 1 },
                refreshUi = { refreshCount += 1 }
            )
        }
    }
}

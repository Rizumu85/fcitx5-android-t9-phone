/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9PunctuationCoordinatorTest {

    private class Host {
        var clearCount = 0
        var refreshCount = 0
        var cancelTimeoutCount = 0
        val commits = mutableListOf<String>()

        fun coordinator() = T9PunctuationCoordinator(
            session = T9PunctuationSession(
                chinesePunctuation = listOf("，", "。"),
                englishPunctuation = listOf("!", "?")
            ),
            clearTransientInputUiState = { clearCount++ },
            refreshUi = { refreshCount++ },
            cancelTimeout = { cancelTimeoutCount++ },
            commitText = { commits += it }
        )
    }

    @Test
    fun showEnglishCandidatesClearsRefreshesAndCancelsTimeout() {
        val host = Host()
        val coordinator = host.coordinator()

        coordinator.showEnglishCandidates()

        assertTrue(coordinator.isPending)
        assertEquals("!", coordinator.pendingText)
        assertEquals(1, host.clearCount)
        assertEquals(1, host.refreshCount)
        assertEquals(1, host.cancelTimeoutCount)
        assertTrue(host.commits.isEmpty())
    }

    @Test
    fun previewCandidateOnlyMovesSelectionAndRefreshes() {
        val host = Host()
        val coordinator = host.coordinator()
        coordinator.showEnglishCandidates()

        assertTrue(coordinator.previewCandidate(1))

        assertTrue(coordinator.isPending)
        assertEquals("?", coordinator.pendingText)
        assertEquals(1, host.clearCount)
        assertEquals(2, host.refreshCount)
        assertEquals(1, host.cancelTimeoutCount)
        assertTrue(host.commits.isEmpty())
    }

    @Test
    fun selectAndCommitCandidateCommitsSelectionAndClearsPendingState() {
        val host = Host()
        val coordinator = host.coordinator()
        coordinator.showEnglishCandidates()

        assertTrue(coordinator.selectAndCommitCandidate(1))

        assertFalse(coordinator.isPending)
        assertNull(coordinator.pendingText)
        assertEquals(listOf("?"), host.commits)
        assertEquals(1, host.clearCount)
        assertEquals(2, host.refreshCount)
        assertEquals(2, host.cancelTimeoutCount)
    }

    @Test
    fun showChineseCandidatesStartsChineseSet() {
        val host = Host()
        val coordinator = host.coordinator()

        coordinator.showChineseCandidates()

        assertTrue(coordinator.isPending)
        assertEquals("，", coordinator.pendingText)
        assertEquals(1, host.clearCount)
        assertEquals(1, host.refreshCount)
        assertEquals(1, host.cancelTimeoutCount)
        assertTrue(host.commits.isEmpty())
    }

    @Test
    fun cancelClearsPendingStateAndRefreshesWithoutCommit() {
        val host = Host()
        val coordinator = host.coordinator()
        coordinator.showEnglishCandidates()

        assertTrue(coordinator.cancel())

        assertFalse(coordinator.isPending)
        assertNull(coordinator.pendingText)
        assertEquals(1, host.clearCount)
        assertEquals(2, host.refreshCount)
        assertEquals(2, host.cancelTimeoutCount)
        assertTrue(host.commits.isEmpty())
    }
}

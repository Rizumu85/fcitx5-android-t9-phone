/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9PunctuationLifecycleTest {

    @Test
    fun showEnglishCandidatesStartsPendingAndReturnsUiEffects() {
        val lifecycle = lifecycle()

        val result = lifecycle.showEnglishCandidates()

        assertTrue(result.handled)
        assertTrue(lifecycle.isPending)
        assertEquals("!", lifecycle.pendingText)
        assertEquals(
            listOf(
                T9PunctuationLifecycle.Effect.ClearTransientInputUiState,
                T9PunctuationLifecycle.Effect.RefreshUi,
                T9PunctuationLifecycle.Effect.CancelTimeout
            ),
            result.effects
        )
    }

    @Test
    fun showChineseCandidatesStartsPendingAndReturnsUiEffects() {
        val lifecycle = lifecycle()

        val result = lifecycle.showChineseCandidates()

        assertTrue(result.handled)
        assertTrue(lifecycle.isPending)
        assertEquals("，", lifecycle.pendingText)
        assertEquals(
            listOf(
                T9PunctuationLifecycle.Effect.ClearTransientInputUiState,
                T9PunctuationLifecycle.Effect.RefreshUi,
                T9PunctuationLifecycle.Effect.CancelTimeout
            ),
            result.effects
        )
    }

    @Test
    fun candidatePreviewRefreshesWithoutCommit() {
        val lifecycle = lifecycle()
        lifecycle.showEnglishCandidates()

        val result = lifecycle.previewCandidate(1)

        assertTrue(result.handled)
        assertEquals("?", lifecycle.pendingText)
        assertEquals(
            listOf(T9PunctuationLifecycle.Effect.RefreshUi),
            result.effects
        )
    }

    @Test
    fun candidateCommitReturnsCommitThenRefreshEffects() {
        val lifecycle = lifecycle()
        lifecycle.showEnglishCandidates()

        val result = lifecycle.selectAndCommitCandidate(1)

        assertTrue(result.handled)
        assertFalse(lifecycle.isPending)
        assertEquals(
            listOf(
                T9PunctuationLifecycle.Effect.CancelTimeout,
                T9PunctuationLifecycle.Effect.CommitText("?"),
                T9PunctuationLifecycle.Effect.RefreshUi
            ),
            result.effects
        )
    }

    private fun lifecycle(): T9PunctuationLifecycle =
        T9PunctuationLifecycle(
            T9PunctuationSession(
                chinesePunctuation = listOf("，", "。"),
                englishPunctuation = listOf("!", "?")
            )
        )
}

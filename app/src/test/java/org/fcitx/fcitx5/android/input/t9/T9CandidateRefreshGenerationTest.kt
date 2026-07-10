/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class T9CandidateRefreshGenerationTest {

    @Test
    fun repeatedRequestsForSameInputCoalesceIntoOneGeneration() {
        val posted = mutableListOf<() -> Unit>()
        val refreshed = mutableListOf<T9CandidateRefreshGeneration.Generation>()
        val coordinator = T9CandidateRefreshGeneration(posted::add, refreshed::add)

        val first = coordinator.requestRefresh(traceInputId = 7L)
        val repeated = coordinator.requestRefresh(traceInputId = 7L)

        assertEquals(first, repeated)
        assertEquals(1, posted.size)

        posted.single().invoke()

        assertEquals(listOf(first), refreshed)
    }

    @Test
    fun cancelledCallbackCannotConsumeReplacementGeneration() {
        val posted = mutableListOf<() -> Unit>()
        val refreshed = mutableListOf<T9CandidateRefreshGeneration.Generation>()
        val coordinator = T9CandidateRefreshGeneration(posted::add, refreshed::add)

        coordinator.requestRefresh(traceInputId = 1L)
        coordinator.cancel()
        val replacement = coordinator.requestRefresh(traceInputId = 2L)

        posted.first().invoke()
        assertEquals(emptyList<T9CandidateRefreshGeneration.Generation>(), refreshed)

        posted.last().invoke()
        assertEquals(listOf(replacement), refreshed)
    }

    @Test
    fun newerInputSupersedesPendingGenerationWithoutExplicitCancel() {
        val posted = mutableListOf<() -> Unit>()
        val refreshed = mutableListOf<T9CandidateRefreshGeneration.Generation>()
        val coordinator = T9CandidateRefreshGeneration(posted::add, refreshed::add)

        coordinator.requestRefresh(traceInputId = 1L)
        val replacement = coordinator.requestRefresh(traceInputId = 2L)

        posted.first().invoke()
        posted.last().invoke()

        assertEquals(listOf(replacement), refreshed)
    }

    @Test
    fun readySourcePublishesPendingGenerationAndStalesItsPostedCallback() {
        val posted = mutableListOf<() -> Unit>()
        val refreshed = mutableListOf<T9CandidateRefreshGeneration.Generation>()
        val coordinator = T9CandidateRefreshGeneration(posted::add, refreshed::add)

        val pending = coordinator.requestRefresh(traceInputId = 7L)
        val published = coordinator.publishReady(traceInputId = 7L)

        assertEquals(pending, published)
        assertEquals(listOf(pending), refreshed)

        posted.single().invoke()

        assertEquals(listOf(pending), refreshed)
    }
}

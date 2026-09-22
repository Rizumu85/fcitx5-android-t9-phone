/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9FilteredCandidateScanTest {
    @Test
    fun unmatchedBatchesDoNotExhaustTheMatchingBudget() {
        val scan = T9FilteredCandidateScan(batchSize = 3, matchLimit = 2) {
            it.takeIf { text -> text.startsWith("match") }
        }
        repeat(4) {
            scan.append(listOf("other-a", "other-b", "other-c"))
            assertFalse(scan.complete)
        }
        scan.append(listOf("other", "match-a", "match-b"))

        assertTrue(scan.complete)
        assertEquals(15, scan.nextOffset)
        assertEquals(listOf(13, 14), scan.candidates.map { it.index })
        assertEquals(listOf("match-a", "match-b"), scan.candidates.map { it.value })
    }

    @Test
    fun duplicatesDoNotUseTheVisibleCandidateBudget() {
        val scan = T9FilteredCandidateScan(batchSize = 3, matchLimit = 2) { it }
        scan.append(listOf("same", "same", "same"))
        assertFalse(scan.complete)
        scan.append(listOf("same", "next", "extra"))

        assertTrue(scan.complete)
        assertEquals(listOf(IndexedValue(0, "same"), IndexedValue(4, "next")), scan.candidates)
    }

    @Test
    fun exhaustedSourceCompletesEvenWithoutMatchingCandidates() {
        val scan = T9FilteredCandidateScan(batchSize = 2) { null }
        scan.append(listOf("other", "other"))
        assertFalse(scan.complete)
        scan.append(emptyList())

        assertTrue(scan.complete)
        assertTrue(scan.candidates.isEmpty())
    }

    @Test
    fun sparseSourceIndicesSurviveFilteringAndPaging() {
        val loader = T9BulkCandidateLoader({ 4 }, { null }) { candidate, prefix ->
            candidate.comment == prefix
        }
        val scan = T9FilteredCandidateScan(batchSize = 2, matchLimit = 2) { raw ->
            T9BulkCandidateLoader.parseCandidate(raw)?.takeIf { it.comment == "selected" }?.text
        }
        scan.append(listOf("x other", "1234 selected"))
        scan.append(listOf("y other", "5678 selected"))
        loader.startRequest(listOf("selected"), "source")
        val first = loader.finishRequest("source", scan.candidates, listOf("selected"))!!.page!!

        assertEquals(listOf(1), first.originalIndices.toList())
        assertEquals(listOf(3), loader.offset(1)!!.originalIndices.toList())
    }
}

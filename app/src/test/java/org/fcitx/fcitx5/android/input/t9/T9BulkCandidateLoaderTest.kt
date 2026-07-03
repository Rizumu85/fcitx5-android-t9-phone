/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9BulkCandidateLoaderTest {

    @Test
    fun parsesRawCandidatesWithOptionalComments() {
        val loader = loader()

        assertEquals(candidate("你", "ni"), loader.parseCandidate("你 ni"))
        assertEquals(candidate("你", ""), loader.parseCandidate("你"))
        assertNull(loader.parseCandidate("   "))
    }

    @Test
    fun finishRequestFiltersDedupesAndBuildsFirstPage() {
        val loader = loader(
            budget = 4,
            matches = { candidate, prefix -> candidate.comment.startsWith(prefix) }
        )
        val signature = "sig"

        assertTrue(loader.startRequest(listOf("ni"), signature))
        val result = loader.finishRequest(
            signature = signature,
            rawCandidates = listOf("你 ni", "呢 ni", "你 ni duplicate", "好 hao"),
            prefixes = listOf("ni")
        )

        assertNotNull(result)
        result!!
        assertEquals("ni", result.matchedPrefix)
        assertEquals(listOf("你", "呢"), result.page!!.candidates.map { it.value.text })
        assertFalse(loader.pending)
    }

    @Test
    fun staleRequestResultIsIgnored() {
        val loader = loader()
        loader.startRequest(emptyList(), "new")

        assertNull(loader.finishRequest("old", listOf("你 ni"), emptyList()))
        assertTrue(loader.pending)
    }

    private fun loader(
        budget: Int = 8,
        matches: (FcitxEvent.Candidate, String) -> Boolean = { candidate, prefix ->
            candidate.comment.startsWith(prefix)
        }
    ): T9BulkCandidateLoader =
        T9BulkCandidateLoader(
            characterBudget = { budget },
            candidateMatchesPrefix = matches
        )

    private fun candidate(text: String, comment: String): FcitxEvent.Candidate =
        FcitxEvent.Candidate(label = "", text = text, comment = comment)
}

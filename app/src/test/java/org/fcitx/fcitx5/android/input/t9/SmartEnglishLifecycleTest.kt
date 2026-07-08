/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEnglishLifecycleTest {

    @Test
    fun commitCandidateMovesFromDigitsToPredictionContext() {
        val host = Host()
        val lifecycle = host.lifecycle()

        listOf(4, 6, 6).forEach(lifecycle::appendDigit)

        assertTrue(lifecycle.commitCandidate())

        assertEquals(listOf("good "), host.committedTexts)
        assertEquals("morning", lifecycle.paged()?.candidates?.first()?.text)
    }

    @Test
    fun learnedWordsFeedPairFrequencyReranking() {
        val host = Host(learn = true)
        val lifecycle = host.lifecycle()

        "Good Morning ".forEach(lifecycle::recordLearningChar)
        listOf(4, 6, 6).forEach(lifecycle::appendDigit)
        lifecycle.commitCandidate()
        "6676464".forEach { lifecycle.appendDigit(it.digitToInt()) }

        assertEquals(listOf("good", "morning"), host.learnedWords)
        assertTrue(host.learnedPredictionPairs.contains("good" to "morning"))
        assertEquals("morning", lifecycle.paged()?.candidates?.first()?.text)
    }

    private class Host(
        private val learn: Boolean = false
    ) {
        private val candidates = mapOf(
            "466" to listOf("good", "home"),
            "6676464" to listOf("mopping", "morning")
        )
        private val predictions = mapOf(
            "good" to listOf("morning", "night")
        )
        val committedTexts = mutableListOf<String>()
        val learnedWords = mutableListOf<String>()
        val learnedPredictionPairs = mutableListOf<Pair<String, String>>()

        fun lifecycle(): SmartEnglishLifecycle =
            SmartEnglishLifecycle(
                candidateProvider = { digits, limit -> candidates[digits].orEmpty().take(limit) },
                predictionProvider = { words, limit -> predictions[words.last()].orEmpty().take(limit) },
                learnWord = { learnedWords += it },
                learnPredictionPair = { previous, next -> learnedPredictionPairs += previous to next },
                dictionaryReady = { true },
                predictionReady = { true },
                candidateLimit = 10,
                noMatchText = "No match",
                isActive = { true },
                shouldLearnWords = { learn },
                commitText = { committedTexts += it },
                refreshUi = {}
            )
    }
}

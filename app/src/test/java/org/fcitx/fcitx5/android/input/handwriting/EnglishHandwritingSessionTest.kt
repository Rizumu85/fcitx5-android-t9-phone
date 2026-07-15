/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EnglishHandwritingSessionTest {
    @Test
    fun smartRecognitionUsesEditorContextAndContinuesWithPredictions() {
        val fixture = Fixture()
        fixture.session.begin("say hello ")

        assertEquals(
            listOf("world", "word"),
            fixture.session.rerank(listOf("word", "world"), suggestionsEnabled = true)
        )
        val predictions = fixture.session.commitWord(
            rawWord = "world",
            emittedText = "world ",
            suggestionsEnabled = true,
            shouldLearn = true,
            continuePrediction = true,
            learnWord = true
        )

        assertEquals(listOf("again", "today"), predictions)
        assertEquals(listOf("world"), fixture.learnedWords)
        assertEquals(listOf("hello" to "world"), fixture.learnedPairs)
        assertEquals(listOf("say", "hello", "world"), fixture.predictionContexts.single())
    }

    @Test
    fun basicEnglishPreservesMlKitOrderWithoutLearningOrPredicting() {
        val fixture = Fixture()
        fixture.session.begin("hello ")

        assertEquals(
            listOf("word", "world"),
            fixture.session.rerank(listOf("word", "world"), suggestionsEnabled = false)
        )
        assertEquals(
            emptyList<String>(),
            fixture.session.commitWord(
                rawWord = "world",
                emittedText = "world ",
                suggestionsEnabled = false,
                shouldLearn = true,
                continuePrediction = true,
                learnWord = true
            )
        )

        assertFalse(fixture.rerankCalled)
        assertEquals(emptyList<String>(), fixture.learnedWords)
        assertEquals(emptyList<Pair<String, String>>(), fixture.learnedPairs)
        assertEquals(emptyList<List<String>>(), fixture.predictionContexts)
    }

    @Test
    fun selectingPredictionLearnsOnlyThePair() {
        val fixture = Fixture()
        fixture.session.begin("hello ")

        fixture.session.commitWord(
            rawWord = "there",
            emittedText = "there ",
            suggestionsEnabled = true,
            shouldLearn = true,
            continuePrediction = true,
            learnWord = false
        )

        assertEquals(emptyList<String>(), fixture.learnedWords)
        assertEquals(listOf("hello" to "there"), fixture.learnedPairs)
    }

    private class Fixture {
        var rerankCalled = false
        val learnedWords = mutableListOf<String>()
        val learnedPairs = mutableListOf<Pair<String, String>>()
        val predictionContexts = mutableListOf<List<String>>()
        val session = EnglishHandwritingSession(
            candidateLimit = 10,
            rerankRecognitions = { candidates, context, limit ->
                rerankCalled = true
                assertEquals(listOf("say", "hello"), context)
                candidates.reversed().take(limit)
            },
            predictionsAfter = { context, _ ->
                predictionContexts += context
                listOf("again", "today")
            },
            learnWord = learnedWords::add,
            learnPair = { previous, next -> learnedPairs += previous to next }
        )
    }
}

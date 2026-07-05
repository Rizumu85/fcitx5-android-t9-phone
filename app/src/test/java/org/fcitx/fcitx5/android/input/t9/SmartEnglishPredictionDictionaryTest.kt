/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SmartEnglishPredictionDictionaryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun builtInPredictionsUseMostRecentContextWord() {
        val dictionary = dictionary(
            "good" to listOf("morning" to 4, "night" to 3),
            "hello" to listOf("there" to 2)
        )

        assertEquals(
            listOf("morning", "night"),
            dictionary.predictionsAfter(listOf("well", "good"), limit = 5)
        )
    }

    @Test
    fun learnedPredictionRanksBeforeBuiltInPrediction() {
        val file = temporaryFolder.newFile("english-next-learned.tsv")
        val dictionary = dictionary(
            "good" to listOf("morning" to 4, "night" to 3),
            learnedFile = file
        )

        dictionary.learn("good", "idea")

        assertEquals(
            listOf("idea", "morning", "night"),
            dictionary.predictionsAfter(listOf("good"), limit = 5)
        )
    }

    @Test
    fun learnedPairsAreReloadedFromDisk() {
        val file = temporaryFolder.newFile("english-next-learned.tsv")
        val first = dictionary(learnedFile = file)
        first.learn("see", "you")

        val second = dictionary(learnedFile = file)

        assertEquals(listOf("you"), second.predictionsAfter(listOf("see"), limit = 5))
    }

    private fun dictionary(
        vararg pairs: Pair<String, List<Pair<String, Int>>>,
        learnedFile: java.io.File? = null
    ): SmartEnglishPredictionDictionary =
        SmartEnglishPredictionDictionary(
            builtInPairs = pairs.toMap().mapValues { (_, predictions) ->
                predictions.map { (word, score) ->
                    SmartEnglishPredictionDictionary.Prediction(word, score)
                }
            },
            learnedFile = learnedFile
        )
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import java.util.Locale

/** Shared immutable-query surface for T9 and handwriting English input sessions. */
class EnglishSuggestionEngine(
    private val dictionary: T9EnglishDictionary = T9EnglishDictionary.Shared,
    private val predictionDictionary: SmartEnglishPredictionDictionary =
        SmartEnglishPredictionDictionary.Shared,
    private val customDictionaryCoordinator: EnglishCustomDictionaryCoordinator? = null
) {
    val isReady: Boolean
        get() = dictionary.isReady && predictionDictionary.isReady

    val dictionaryGeneration: Long
        get() = customDictionaryCoordinator?.smartEnglishGeneration() ?: dictionary.generation

    val predictionGeneration: Long
        get() = predictionDictionary.generation

    fun preload() {
        dictionary.preload()
        predictionDictionary.preload()
    }

    fun candidatesForDigits(digits: String, limit: Int): List<String> {
        val primary = dictionary.candidatesFor(digits, limit)
        val additional = customDictionaryCoordinator
            ?.additionalCandidatesForSmartEnglish(digits, limit)
            .orEmpty()
        if (additional.isEmpty()) return primary
        // Keep the dictionary's first high-frequency candidate stable, then let shared custom
        // words outrank generic completions so sharing has a visible effect without breaking "I".
        return EnglishCustomDictionaryCoordinator.mergeDistinct(
            primary.take(1),
            additional + primary.drop(1),
            limit
        )
    }

    fun predictionsAfter(previousWords: List<String>, limit: Int): List<String> =
        predictionDictionary.predictionsAfter(previousWords, limit)

    fun learnWord(word: String) {
        customDictionaryCoordinator?.learnFromSmartEnglish(word) ?: dictionary.learn(word)
    }

    fun learnPair(previous: String, next: String) = predictionDictionary.learn(previous, next)

    fun rerankRecognitions(
        candidates: List<String>,
        previousWords: List<String>,
        limit: Int
    ): List<String> {
        if (candidates.size < 2) return candidates.take(limit)
        val predictionRanks = predictionsAfter(previousWords, RecognitionPredictionLimit)
            .mapIndexed { index, word -> word.lowercase(Locale.US) to index }
            .toMap()
        return candidates.withIndex()
            .sortedWith(
                compareBy<IndexedValue<String>> { candidate ->
                    val normalized = candidate.value.lowercase(Locale.US)
                    val pairBoost = predictionRanks[normalized]?.let(::pairRankBoost) ?: 0f
                    val learnedBoost = if (dictionary.isLearned(candidate.value)) LearnedWordBoost else 0f
                    val dictionaryBoost = dictionary.exactWordRank(candidate.value)
                        ?.let(::dictionaryRankBoost)
                        ?: 0f
                    candidate.index - pairBoost - learnedBoost - dictionaryBoost
                }.thenBy(IndexedValue<String>::index)
            )
            .map(IndexedValue<String>::value)
            .distinctBy { it.lowercase(Locale.US) }
            .take(limit)
    }

    companion object {
        val Shared by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            EnglishSuggestionEngine(
                customDictionaryCoordinator = EnglishCustomDictionaryCoordinator.Shared
            )
        }

        fun normalizeContextWord(rawWord: String): String? {
            val word = rawWord.trim().lowercase(Locale.US)
            return word.takeIf { it.isNotEmpty() && it.all { char -> char in 'a'..'z' } }
        }

        private fun pairRankBoost(rank: Int): Float = when (rank) {
            0 -> 3f
            1 -> 2f
            2 -> 1f
            else -> 0f
        }

        private fun dictionaryRankBoost(rank: Int): Float = when (rank) {
            0 -> 0.5f
            in 1..3 -> 0.25f
            else -> 0f
        }

        private const val RecognitionPredictionLimit = 32
        private const val LearnedWordBoost = 1f
    }
}

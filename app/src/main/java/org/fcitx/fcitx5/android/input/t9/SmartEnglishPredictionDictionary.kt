/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.utils.appContext
import java.io.File
import java.util.LinkedHashMap
import java.util.concurrent.Executor

class SmartEnglishPredictionDictionary(
    private val builtInAsset: String = BuiltInPredictionAsset,
    learnedFile: File? = File(appContext.filesDir, "t9/english-next-learned.tsv"),
    persistenceExecutor: Executor? = null
) {
    constructor(
        builtInPairs: Map<String, List<Prediction>>,
        learnedFile: File? = null,
        persistenceExecutor: Executor? = null
    ) : this(
        builtInAsset = BuiltInPredictionAsset,
        learnedFile = learnedFile,
        persistenceExecutor = persistenceExecutor
    ) {
        builtInPredictionsByPrevious = builtInPairs
        builtInReady = true
    }

    data class Prediction(
        val word: String,
        val score: Int
    )

    private val learnedPersistence = SmartEnglishPersistence(
        file = learnedFile,
        defaultValue = emptyMap(),
        decode = { lines -> lines.mapNotNull(::parsePredictionLine).toMap() },
        encode = ::encodeLearnedPredictions,
        executor = persistenceExecutor ?: SmartEnglishPersistence.DefaultExecutor
    )
    private var learnedPredictionsByPrevious: Map<String, List<Prediction>> =
        learnedPersistence.snapshot()
    private val predictionCache = object : LinkedHashMap<String, List<String>>(
        PredictionCacheSize,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
            size > PredictionCacheSize
    }

    @Volatile
    private var builtInPredictionsByPrevious: Map<String, List<Prediction>> = emptyMap()

    @Volatile
    private var builtInReady = false

    val isReady: Boolean
        get() = builtInReady

    fun preload() {
        if (builtInReady) return
        val loaded = loadBuiltInPredictions()
        synchronized(this) {
            if (builtInReady) return
            builtInPredictionsByPrevious = loaded
            builtInReady = true
            predictionCache.clear()
        }
    }

    @Synchronized
    fun predictionsAfter(previousWords: List<String>, limit: Int): List<String> =
        T9ResponsivenessTrace.measure("SmartEnglishPredictionDictionary.predictionsAfter") {
            val previous = previousWords.asReversed()
                .firstNotNullOfOrNull(::normalizePredictionWord)
                ?: return emptyList()
            val cacheKey = "$previous|$limit"
            predictionCache[cacheKey]?.let { return it }
            val predictions = ArrayList<String>(limit)
            val seen = HashSet<String>()

            fun add(word: String): Boolean {
                if (!seen.add(word)) return false
                predictions += word
                return predictions.size >= limit
            }

            learnedPredictionsByPrevious[previous]
                ?.forEach { if (add(it.word)) return cachePredictions(cacheKey, predictions) }
            if (builtInReady) {
                builtInPredictionsByPrevious[previous]
                    ?.forEach { if (add(it.word)) return cachePredictions(cacheKey, predictions) }
            }
            return cachePredictions(cacheKey, predictions)
        }

    @Synchronized
    fun learn(previousRaw: String, nextRaw: String) {
        val previous = normalizePredictionWord(previousRaw) ?: return
        val next = normalizePredictionWord(nextRaw) ?: return
        if (previous == next) return
        val mutable = learnedPredictionsByPrevious
            .mapValues { (_, predictions) -> predictions.toMutableList() }
            .toMutableMap()
        val predictions = mutable.getOrPut(previous) { mutableListOf() }
        val existingIndex = predictions.indexOfFirst { it.word == next }
        if (existingIndex >= 0) {
            val existing = predictions[existingIndex]
            predictions[existingIndex] = existing.copy(score = existing.score + 1)
        } else {
            predictions += Prediction(next, 1)
        }
        learnedPredictionsByPrevious = mutable.mapValues { (_, predictions) ->
            predictions.sortedWith(PredictionQuality)
        }
        predictionCache.clear()
        learnedPersistence.replace(learnedPredictionsByPrevious)
    }

    @Synchronized
    fun learnedPairs(): Map<String, List<Prediction>> {
        return learnedPredictionsByPrevious
    }

    @Synchronized
    fun replaceLearnedPairs(pairs: Map<String, List<Prediction>>) {
        learnedPredictionsByPrevious = pairs.mapNotNull { (previousRaw, predictions) ->
            val previous = normalizePredictionWord(previousRaw) ?: return@mapNotNull null
            val normalizedPredictions = predictions.mapNotNull { prediction ->
                val word = normalizePredictionWord(prediction.word) ?: return@mapNotNull null
                Prediction(word, prediction.score.coerceAtLeast(1))
            }
                .groupBy { it.word }
                .map { (word, grouped) -> Prediction(word, grouped.sumOf { it.score }) }
                .sortedWith(PredictionQuality)
            previous.takeIf { normalizedPredictions.isNotEmpty() }?.let { it to normalizedPredictions }
        }.toMap()
        predictionCache.clear()
        learnedPersistence.replace(learnedPredictionsByPrevious)
    }

    private fun cachePredictions(cacheKey: String, predictions: List<String>): List<String> {
        val cached = predictions.toList()
        predictionCache[cacheKey] = cached
        return cached
    }

    private fun loadBuiltInPredictions(): Map<String, List<Prediction>> {
        val predictions = linkedMapOf<String, List<Prediction>>()
        runCatching {
            appContext.assets.open(builtInAsset).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    parsePredictionLine(line)?.let { (previous, nextWords) ->
                        predictions[previous] = nextWords
                    }
                }
            }
        }
        return predictions
    }

    companion object {
        val Shared by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            SmartEnglishPredictionDictionary()
        }

        private const val BuiltInPredictionAsset = "t9/english-next.tsv"
        private const val PredictionCacheSize = 96

        private val PredictionQuality = compareByDescending<Prediction> { it.score }
            .thenBy { it.word }

        fun normalizePredictionWord(rawWord: String): String? =
            T9EnglishDictionary.normalizeLearnedWord(
                rawWord.trim().filter { it in 'a'..'z' || it in 'A'..'Z' }
            )

        private fun parsePredictionLine(line: String): Pair<String, List<Prediction>>? {
            val parts = line.split('\t')
            val previous = parts.firstOrNull()?.let(::normalizePredictionWord) ?: return null
            val predictions = parts.drop(1).mapNotNull { raw ->
                val separator = raw.lastIndexOf(':')
                if (separator <= 0 || separator == raw.lastIndex) return@mapNotNull null
                val word = normalizePredictionWord(raw.substring(0, separator)) ?: return@mapNotNull null
                val score = raw.substring(separator + 1).toIntOrNull() ?: return@mapNotNull null
                Prediction(word, score.coerceAtLeast(1))
            }.sortedWith(PredictionQuality)
            return previous.takeIf { predictions.isNotEmpty() }?.let { it to predictions }
        }

        private fun encodeLearnedPredictions(
            learned: Map<String, List<Prediction>>
        ): String = buildString {
            learned.toSortedMap().forEach { (previous, predictions) ->
                append(previous)
                predictions.forEach { prediction ->
                    append('\t')
                        .append(prediction.word)
                        .append(':')
                        .append(prediction.score.coerceAtLeast(1))
                }
                append('\n')
            }
        }
    }
}

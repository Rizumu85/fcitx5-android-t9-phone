/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.utils.appContext
import java.io.File
import java.util.LinkedHashMap
import java.util.Locale
import java.util.PriorityQueue
import java.util.TreeMap

class T9EnglishDictionary {
    private val learnedFile = File(appContext.filesDir, "t9/english-learned.txt")
    private val learnedPersistence = SmartEnglishPersistence(
        file = learnedFile,
        defaultValue = emptySet(),
        decode = { lines -> lines.mapNotNull(::normalizeLearnedWord).toSet() },
        encode = { words -> words.sorted().joinToString(separator = "\n", postfix = "\n") }
    )
    private val learnedWordSet = learnedPersistence.snapshot().toMutableSet()
    private var learnedExactWordsByDigits: Map<String, List<String>> = emptyMap()
    private var learnedPrefixWordsByDigits: Map<String, List<String>> = emptyMap()
    private val candidateCache = object : LinkedHashMap<String, List<String>>(
        CandidateCacheSize,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
            size > CandidateCacheSize
    }
    private val builtInWordsByDigits by lazy(LazyThreadSafetyMode.PUBLICATION) {
        loadBuiltInWords()
    }
    private val builtInPrefixWordsByDigits by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildBuiltInPrefixWordsByDigits(builtInWordsByDigits)
    }
    @Volatile
    private var builtInReady = false

    init {
        rebuildLearnedIndexes()
    }

    val isReady: Boolean
        get() = builtInReady

    fun preload() {
        if (builtInReady) return
        builtInWordsByDigits.size
        builtInPrefixWordsByDigits.size
        synchronized(this) {
            builtInReady = true
            invalidateCandidateCache()
            warmCommonCandidateCache()
        }
    }

    @Synchronized
    fun candidatesFor(digits: String, limit: Int = 10): List<String> =
        T9ResponsivenessTrace.measure("T9EnglishDictionary.candidatesFor") {
        if (digits.isEmpty()) return emptyList()
        val cacheKey = "$digits|$limit"
        candidateCache[cacheKey]?.let { return it }
        val candidates = ArrayList<String>(limit)
        val seen = HashSet<String>()

        fun addCandidate(word: String): Boolean {
            val key = word.lowercase(Locale.US)
            if (!seen.add(key)) return false
            candidates += word
            return candidates.size >= limit
        }

        EssentialWordsByDigits[digits]
            ?.forEach { if (addCandidate(it)) return candidates }

        learnedExactWordsByDigits[digits]
            ?.forEach { if (addCandidate(it)) return cacheCandidates(cacheKey, candidates) }

        if (builtInReady) {
            builtInWordsByDigits[digits]
                ?.forEach { if (addCandidate(it.word)) return cacheCandidates(cacheKey, candidates) }
        }

        learnedPrefixWordsByDigits[digits]
            ?.forEach { if (addCandidate(it)) return cacheCandidates(cacheKey, candidates) }

        if (builtInReady) {
            builtInPrefixWordsByDigits[digits]
                ?.forEach { if (addCandidate(it.word)) return cacheCandidates(cacheKey, candidates) }
        }

        return cacheCandidates(cacheKey, candidates)
    }

    @Synchronized
    fun learn(rawWord: String) {
        val word = normalizeLearnedWord(rawWord) ?: return
        if (!learnedWordSet.add(word)) return
        rebuildLearnedIndexes()
        invalidateCandidateCache()
        learnedPersistence.replace(learnedWordSet.toSet())
    }

    @Synchronized
    fun learnedWords(): List<String> {
        return learnedWordSet.sorted()
    }

    @Synchronized
    fun replaceLearnedWords(rawWords: Iterable<String>) {
        learnedWordSet.clear()
        rawWords
            .mapNotNull(::normalizeLearnedWord)
            .distinct()
            .sorted()
            .forEach { learnedWordSet += it }
        rebuildLearnedIndexes()
        invalidateCandidateCache()
        learnedPersistence.replace(learnedWordSet.toSet())
    }

    private fun cacheCandidates(cacheKey: String, candidates: List<String>): List<String> {
        val cached = candidates.toList()
        candidateCache[cacheKey] = cached
        return cached
    }

    private fun invalidateCandidateCache() {
        candidateCache.clear()
    }

    private fun warmCommonCandidateCache() {
        WarmupDigitSequences.forEach { digits ->
            candidatesFor(digits, WarmupCandidateLimit)
        }
    }

    private fun rebuildLearnedIndexes() {
        val exact = linkedMapOf<String, MutableList<String>>()
        val prefixes = linkedMapOf<String, MutableList<String>>()
        learnedWordSet.forEach { word ->
            val digits = word.toT9Digits()
            exact.getOrPut(digits) { mutableListOf() } += word
            for (prefixLength in 1 until digits.length) {
                prefixes.getOrPut(digits.substring(0, prefixLength)) { mutableListOf() } += word
            }
        }
        learnedExactWordsByDigits = exact
        learnedPrefixWordsByDigits = prefixes
    }

    private fun loadBuiltInWords(): TreeMap<String, List<BuiltInWord>> {
        val wordsByDigits = TreeMap<String, List<BuiltInWord>>()
        runCatching {
            appContext.assets.open(BuiltInDictionaryAsset).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size < 2) return@forEach
                    val words = parts.drop(1).mapNotNull(::parseBuiltInWord)
                    if (words.isNotEmpty()) {
                        wordsByDigits[parts[0]] = words
                    }
                }
            }
        }
        return wordsByDigits
    }

    private fun buildBuiltInPrefixWordsByDigits(
        exactWordsByDigits: Map<String, List<BuiltInWord>>
    ): Map<String, List<BuiltInWord>> {
        val pools = HashMap<String, PriorityQueue<BuiltInWord>>()
        exactWordsByDigits.forEach { (sequence, words) ->
            for (prefixLength in 1 until sequence.length) {
                val prefix = sequence.substring(0, prefixLength)
                val pool = pools.getOrPut(prefix) { PriorityQueue(BuiltInCandidateQuality) }
                words.forEach { addPrefixCandidate(pool, it) }
            }
        }
        return pools.mapValues { (_, pool) ->
            pool.toList().sortedWith(BuiltInCandidateQuality.reversed())
        }
    }

    private fun addPrefixCandidate(
        pool: PriorityQueue<BuiltInWord>,
        candidate: BuiltInWord
    ) {
        if (pool.size < PrefixCandidatePoolSize) {
            pool += candidate
        } else if (BuiltInCandidateQuality.compare(candidate, pool.peek()) > 0) {
            pool.poll()
            pool += candidate
        }
    }

    private fun parseBuiltInWord(raw: String): BuiltInWord? {
        val separator = raw.lastIndexOf(':')
        if (separator <= 0 || separator == raw.lastIndex) return null
        val word = raw.substring(0, separator)
        val frequency = raw.substring(separator + 1).toIntOrNull() ?: return null
        return BuiltInWord(word, frequency)
    }

    companion object {
        val Shared by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { T9EnglishDictionary() }

        private const val BuiltInDictionaryAsset = "t9/english.tsv"
        private const val PrefixCandidatePoolSize = 64
        private const val CandidateCacheSize = 128
        private const val WarmupCandidateLimit = 10

        fun normalizeLearnedWord(rawWord: String): String? {
            val word = rawWord.trim().lowercase(Locale.US)
            if (word.length < 2) return null
            if (!word.all { it in 'a'..'z' }) return null
            if (word.all { it == word.first() }) return null
            return word
        }

        fun t9DigitsForWord(rawWord: String): String? =
            normalizeLearnedWord(rawWord)?.toT9Digits()

        private val WarmupDigitSequences = listOf(
            "2", "3", "4", "5", "6", "7", "8", "9",
            "26", "36", "43", "46", "48", "66", "73", "84", "93",
            "435", "466", "843"
        )

        private val EssentialWordsByDigits = mapOf(
            "2" to listOf("a", "as", "at"),
            "3" to listOf("do"),
            "4" to listOf("I", "in", "is", "hi"),
            "5" to listOf("like"),
            "6" to listOf("my", "no", "of", "on"),
            "7" to listOf("so", "see", "she"),
            "8" to listOf("to", "the", "this"),
            "9" to listOf("we", "you", "yes"),
            "26" to listOf("am", "an"),
            "36" to listOf("do"),
            "43" to listOf("he", "if"),
            "46" to listOf("go", "in"),
            "48" to listOf("it"),
            "66" to listOf("no", "on"),
            "73" to listOf("see"),
            "84" to listOf("the"),
            "93" to listOf("we"),
            "435" to listOf("hello", "help"),
            "466" to listOf("good", "home"),
            "843" to listOf("the")
        )

        private data class BuiltInWord(
            val word: String,
            val frequency: Int
        )

        private val BuiltInCandidateQuality = Comparator<BuiltInWord> { a, b ->
            when {
                a.frequency != b.frequency -> a.frequency.compareTo(b.frequency)
                a.word.length != b.word.length -> b.word.length.compareTo(a.word.length)
                else -> b.word.compareTo(a.word, ignoreCase = true)
            }
        }

        private fun Char.t9Digit(): Char? = when (this) {
            in 'a'..'c' -> '2'
            in 'd'..'f' -> '3'
            in 'g'..'i' -> '4'
            in 'j'..'l' -> '5'
            in 'm'..'o' -> '6'
            in 'p'..'s' -> '7'
            in 't'..'v' -> '8'
            in 'w'..'z' -> '9'
            else -> null
        }

        private fun String.toT9Digits(): String =
            buildString(length) {
                this@toT9Digits.forEach { char ->
                    char.t9Digit()?.let(::append)
                }
            }

    }
}

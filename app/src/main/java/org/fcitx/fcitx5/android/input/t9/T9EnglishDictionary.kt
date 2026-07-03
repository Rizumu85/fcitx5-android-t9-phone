/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.utils.appContext
import java.io.File
import java.util.Locale
import java.util.PriorityQueue
import java.util.TreeMap

class T9EnglishDictionary {
    private val learnedFile = File(appContext.filesDir, "t9/english-learned.txt")
    private val learnedWords = linkedSetOf<String>()
    private val builtInWordsByDigits by lazy(LazyThreadSafetyMode.PUBLICATION) {
        loadBuiltInWords()
    }

    init {
        loadLearnedWords()
    }

    fun candidatesFor(digits: String, limit: Int = 10): List<String> {
        if (digits.isEmpty()) return emptyList()
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

        learnedWords
            .filter { it.toT9Digits() == digits }
            .forEach { if (addCandidate(it)) return candidates }

        builtInWordsByDigits[digits]
            ?.forEach { if (addCandidate(it.word)) return candidates }

        learnedWords
            .filter { it.toT9Digits().startsWith(digits) }
            .forEach { if (addCandidate(it)) return candidates }

        val prefixCandidates = PriorityQueue(BuiltInCandidateQuality)
        val prefixSeen = HashSet<String>()
        for ((sequence, words) in builtInWordsByDigits.tailMap(digits, false)) {
            if (!sequence.startsWith(digits)) break
            words.forEach { word ->
                val key = word.word.lowercase(Locale.US)
                if (key in seen || !prefixSeen.add(key)) return@forEach
                if (prefixCandidates.size < PrefixCandidatePoolSize) {
                    prefixCandidates += word
                } else if (BuiltInCandidateQuality.compare(word, prefixCandidates.peek()) > 0) {
                    prefixCandidates.poll()
                    prefixCandidates += word
                }
            }
        }

        prefixCandidates
            .toList()
            .sortedWith(BuiltInCandidateQuality.reversed())
            .forEach { if (addCandidate(it.word)) return candidates }

        return candidates
    }

    fun learn(rawWord: String) {
        val word = rawWord
            .trim()
            .lowercase(Locale.US)
            .filter { it in 'a'..'z' }
        if (word.length < 2 || word.all { it == word.first() }) return
        if (!learnedWords.add(word)) return
        learnedFile.parentFile?.mkdirs()
        learnedFile.writeText(learnedWords.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun loadLearnedWords() {
        if (!learnedFile.isFile) return
        learnedFile.readLines()
            .map { it.trim().lowercase(Locale.US) }
            .filter { word -> word.length >= 2 && word.all { it in 'a'..'z' } }
            .forEach { learnedWords += it }
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

    private fun parseBuiltInWord(raw: String): BuiltInWord? {
        val separator = raw.lastIndexOf(':')
        if (separator <= 0 || separator == raw.lastIndex) return null
        val word = raw.substring(0, separator)
        val frequency = raw.substring(separator + 1).toIntOrNull() ?: return null
        return BuiltInWord(word, frequency)
    }

    companion object {
        private const val BuiltInDictionaryAsset = "t9/english.tsv"
        private const val PrefixCandidatePoolSize = 64

        private val EssentialWordsByDigits = mapOf(
            "2" to listOf("a"),
            "4" to listOf("I")
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

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import java.util.LinkedHashMap
import java.util.PriorityQueue
import java.util.TreeMap

internal class T9EnglishBuiltInIndex private constructor(
    private val exactWordsByDigits: TreeMap<String, List<Word>>,
    private val prefixPoolSize: Int
) {
    private data class Word(
        val text: String,
        val frequency: Int
    )

    private val prefixCache = object : LinkedHashMap<String, List<String>>(
        PrefixCacheSize,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
            size > PrefixCacheSize
    }

    fun exactWords(digits: String): List<String> =
        exactWordsByDigits[digits].orEmpty().map(Word::text)

    @Synchronized
    fun prefixWords(digits: String): List<String> {
        prefixCache[digits]?.let { return it }
        val pool = PriorityQueue<Word>(CandidateQuality)
        exactWordsByDigits.tailMap(digits).entries
            .asSequence()
            .takeWhile { (sequence) -> sequence.startsWith(digits) }
            .filter { (sequence) -> sequence.length > digits.length }
            .forEach { (_, words) ->
                words.forEach { word ->
                    if (pool.size < prefixPoolSize) {
                        pool += word
                    } else if (CandidateQuality.compare(word, pool.peek()) > 0) {
                        pool.poll()
                        pool += word
                    }
                }
            }
        return pool.toList()
            .sortedWith(CandidateQuality.reversed())
            .map(Word::text)
            .also { prefixCache[digits] = it }
    }

    fun warmPrefixes(prefixes: Iterable<String>) {
        prefixes.forEach(::prefixWords)
    }

    companion object {
        private const val PrefixCacheSize = 128
        private const val DefaultPrefixPoolSize = 64

        val Empty = T9EnglishBuiltInIndex(TreeMap(), DefaultPrefixPoolSize)

        fun parse(
            lines: Sequence<String>,
            prefixPoolSize: Int = DefaultPrefixPoolSize
        ): T9EnglishBuiltInIndex {
            val exact = TreeMap<String, List<Word>>()
            lines.forEach { line ->
                val parts = line.split('\t')
                val sequence = parts.firstOrNull()?.takeIf(String::isNotEmpty) ?: return@forEach
                val words = parts.drop(1).mapNotNull(::parseWord)
                if (words.isNotEmpty()) exact[sequence] = words
            }
            return T9EnglishBuiltInIndex(exact, prefixPoolSize.coerceAtLeast(1))
        }

        private fun parseWord(raw: String): Word? {
            val separator = raw.lastIndexOf(':')
            if (separator <= 0 || separator == raw.lastIndex) return null
            val frequency = raw.substring(separator + 1).toIntOrNull() ?: return null
            return Word(raw.substring(0, separator), frequency)
        }

        private val CandidateQuality = Comparator<Word> { a, b ->
            when {
                a.frequency != b.frequency -> a.frequency.compareTo(b.frequency)
                a.text.length != b.text.length -> b.text.length.compareTo(a.text.length)
                else -> b.text.compareTo(a.text, ignoreCase = true)
            }
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.utils.appContext
import java.io.File
import java.util.LinkedHashMap
import java.util.Locale

@Serializable
data class ChineseT9CustomPhrase(
    val text: String,
    val pinyin: String
) {
    companion object {
        fun create(rawText: String, rawPinyin: String): ChineseT9CustomPhrase? {
            val text = rawText.trim()
            val pinyin = normalizePinyin(rawPinyin) ?: return null
            if (text.isEmpty() || !text.containsHanCharacter()) return null
            return ChineseT9CustomPhrase(text = text, pinyin = pinyin)
        }

        fun normalizePinyin(rawPinyin: String): String? {
            val syllables = rawPinyin
                .trim()
                .lowercase(Locale.US)
                .split(Regex("[\\s']+"))
                .filter(String::isNotEmpty)
            if (syllables.isEmpty() || syllables.any { syllable ->
                    !syllable.all { it in 'a'..'z' }
                }
            ) {
                return null
            }
            return syllables.joinToString("'")
        }

        private fun String.containsHanCharacter(): Boolean =
            codePoints().anyMatch { codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
            }
    }
}

class ChineseT9CustomPhraseDictionary internal constructor(
    private val persistence: T9DictionaryPersistence<List<ChineseT9CustomPhrase>>
) {
    constructor() : this(
        T9DictionaryPersistence(
            file = File(appContext.filesDir, "t9/chinese-custom-phrases.json"),
            defaultValue = emptyList(),
            decode = ::decodePhrases,
            encode = PhraseJson::encodeToString
        )
    )

    private var entries = normalizeEntries(persistence.snapshot())
    private var index = PhraseIndex.build(entries)

    @Volatile
    private var dictionaryGeneration = 0L

    val generation: Long
        get() = dictionaryGeneration

    @Synchronized
    fun entries(): List<ChineseT9CustomPhrase> = entries

    @Synchronized
    fun candidatesForDigits(digits: String, limit: Int): List<ChineseT9CustomPhrase> =
        index.candidatesFor(digits, limit)

    @Synchronized
    fun replaceEntries(rawEntries: Iterable<ChineseT9CustomPhrase>) {
        val normalized = normalizeEntries(rawEntries)
        if (normalized == entries) return
        entries = normalized
        index = PhraseIndex.build(entries)
        dictionaryGeneration += 1
        persistence.replace(entries)
    }

    private class PhraseIndex(
        private val exact: Map<String, List<ChineseT9CustomPhrase>>,
        private val prefixes: Map<String, List<ChineseT9CustomPhrase>>
    ) {
        fun candidatesFor(digits: String, limit: Int): List<ChineseT9CustomPhrase> {
            if (digits.isEmpty() || limit <= 0) return emptyList()
            return buildList(limit) {
                val seen = HashSet<String>()
                fun append(values: List<ChineseT9CustomPhrase>) {
                    values.forEach { entry ->
                        if (size < limit && seen.add(entry.text)) add(entry)
                    }
                }
                append(exact[digits].orEmpty())
                append(prefixes[digits].orEmpty())
            }
        }

        companion object {
            fun build(entries: List<ChineseT9CustomPhrase>): PhraseIndex {
                val exact = linkedMapOf<String, MutableList<ChineseT9CustomPhrase>>()
                val prefixes = linkedMapOf<String, MutableList<ChineseT9CustomPhrase>>()
                entries.forEach { entry ->
                    val letters = entry.pinyin.filter { it in 'a'..'z' }
                    val digits = T9EnglishDictionary.t9DigitsForLetters(letters) ?: return@forEach
                    exact.getOrPut(digits) { mutableListOf() } += entry
                    for (length in 1 until digits.length) {
                        prefixes.getOrPut(digits.take(length)) { mutableListOf() } += entry
                    }
                }
                return PhraseIndex(exact, prefixes)
            }
        }
    }

    companion object {
        val Shared by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            ChineseT9CustomPhraseDictionary()
        }

        private val PhraseJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private fun decodePhrases(lines: List<String>): List<ChineseT9CustomPhrase> =
            runCatching {
                PhraseJson.decodeFromString<List<ChineseT9CustomPhrase>>(lines.joinToString("\n"))
            }.getOrDefault(emptyList())

        private fun normalizeEntries(
            rawEntries: Iterable<ChineseT9CustomPhrase>
        ): List<ChineseT9CustomPhrase> =
            rawEntries
                .mapNotNull { ChineseT9CustomPhrase.create(it.text, it.pinyin) }
                .distinctBy { it.text to it.pinyin }
                .sortedWith(compareBy(ChineseT9CustomPhrase::pinyin, ChineseT9CustomPhrase::text))
    }
}

class ChineseT9EnglishDictionary internal constructor(
    private val persistence: T9DictionaryPersistence<Set<String>>
) {
    constructor() : this(
        T9DictionaryPersistence(
            file = File(appContext.filesDir, "t9/chinese-english-learned.txt"),
            defaultValue = emptySet(),
            decode = { lines -> lines.mapNotNull(T9EnglishDictionary::normalizeLearnedWord).toSet() },
            encode = { words -> words.sorted().joinToString(separator = "\n", postfix = "\n") }
        )
    )

    private var words = persistence.snapshot()
        .mapNotNull(T9EnglishDictionary::normalizeLearnedWord)
        .toSet()
    private var index = CustomWordIndex.build(words)

    @Volatile
    private var dictionaryGeneration = 0L

    val generation: Long
        get() = dictionaryGeneration

    @Synchronized
    fun words(): List<String> = words.sorted()

    @Synchronized
    fun candidatesForDigits(digits: String, limit: Int): List<String> =
        index.candidatesFor(digits, limit)

    @Synchronized
    fun contains(rawWord: String): Boolean {
        val word = T9EnglishDictionary.normalizeLearnedWord(rawWord) ?: return false
        return word in words
    }

    @Synchronized
    fun learn(rawWord: String) {
        val word = T9EnglishDictionary.normalizeLearnedWord(rawWord) ?: return
        if (word in words) return
        replaceWords(words + word)
    }

    @Synchronized
    fun replaceWords(rawWords: Iterable<String>) {
        val normalized = rawWords
            .mapNotNull(T9EnglishDictionary::normalizeLearnedWord)
            .toSet()
        if (normalized == words) return
        words = normalized
        index = CustomWordIndex.build(words)
        dictionaryGeneration += 1
        persistence.replace(words)
    }

    companion object {
        val Shared by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            ChineseT9EnglishDictionary()
        }
    }
}

enum class EnglishCustomDictionaryScope {
    SMART_ENGLISH,
    CHINESE
}

class EnglishCustomDictionaryCoordinator(
    private val smartEnglish: T9EnglishDictionary,
    private val chineseEnglish: ChineseT9EnglishDictionary,
    private val sharingEnabled: () -> Boolean
) {
    private var smartLearnedIndexGeneration = Long.MIN_VALUE
    private var smartLearnedIndex = CustomWordIndex.Empty

    fun words(scope: EnglishCustomDictionaryScope): List<String> =
        if (sharingEnabled()) {
            (smartEnglish.learnedWords() + chineseEnglish.words()).distinct().sorted()
        } else {
            when (scope) {
                EnglishCustomDictionaryScope.SMART_ENGLISH -> smartEnglish.learnedWords()
                EnglishCustomDictionaryScope.CHINESE -> chineseEnglish.words()
            }
        }

    fun replaceWords(scope: EnglishCustomDictionaryScope, words: Iterable<String>) {
        if (sharingEnabled()) {
            // A shared management screen represents one logical collection. Replacing both stores
            // makes deletion deterministic and leaves a useful copy in each scope if sharing is
            // later disabled.
            smartEnglish.replaceLearnedWords(words)
            chineseEnglish.replaceWords(words)
        } else {
            when (scope) {
                EnglishCustomDictionaryScope.SMART_ENGLISH ->
                    smartEnglish.replaceLearnedWords(words)
                EnglishCustomDictionaryScope.CHINESE ->
                    chineseEnglish.replaceWords(words)
            }
        }
    }

    fun learnFromSmartEnglish(rawWord: String) {
        smartEnglish.learn(rawWord)
    }

    fun learnFromChinese(rawWord: String) {
        chineseEnglish.learn(rawWord)
    }

    fun isKnownForChinese(rawWord: String): Boolean {
        return chineseEnglish.contains(rawWord)
    }

    fun candidatesForChinese(digits: String, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val chinese = chineseEnglish.candidatesForDigits(digits, limit)
        if (!sharingEnabled() || chinese.size >= limit) return chinese
        val shared = smartLearnedIndex().candidatesFor(digits, limit)
        return mergeDistinct(chinese, shared, limit)
    }

    fun additionalCandidatesForSmartEnglish(digits: String, limit: Int): List<String> =
        if (sharingEnabled()) {
            chineseEnglish.candidatesForDigits(digits, limit)
        } else {
            emptyList()
        }

    fun smartEnglishGeneration(): Long =
        if (sharingEnabled()) {
            smartEnglish.generation * 31L + chineseEnglish.generation * 17L + 1L
        } else {
            smartEnglish.generation * 31L
        }

    @Synchronized
    private fun smartLearnedIndex(): CustomWordIndex {
        val generation = smartEnglish.generation
        if (generation == smartLearnedIndexGeneration) return smartLearnedIndex
        return CustomWordIndex.build(smartEnglish.learnedWords()).also { index ->
            smartLearnedIndex = index
            smartLearnedIndexGeneration = generation
        }
    }

    companion object {
        val Shared by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            EnglishCustomDictionaryCoordinator(
                smartEnglish = T9EnglishDictionary.Shared,
                chineseEnglish = ChineseT9EnglishDictionary.Shared,
                sharingEnabled = {
                    AppPrefs.getInstance().chineseT9.shareEnglishCustomDictionary.getValue()
                }
            )
        }

        internal fun mergeDistinct(
            first: List<String>,
            second: List<String>,
            limit: Int
        ): List<String> = buildList(limit) {
            val seen = HashSet<String>()
            (first + second).forEach { word ->
                if (size < limit && seen.add(word.lowercase(Locale.US))) add(word)
            }
        }
    }
}

private class CustomWordIndex(
    private val exact: Map<String, List<String>>,
    private val prefixes: Map<String, List<String>>
) {
    fun candidatesFor(digits: String, limit: Int): List<String> {
        if (digits.isEmpty() || limit <= 0) return emptyList()
        return EnglishCustomDictionaryCoordinator.mergeDistinct(
            exact[digits].orEmpty(),
            prefixes[digits].orEmpty(),
            limit
        )
    }

    companion object {
        val Empty = CustomWordIndex(emptyMap(), emptyMap())

        fun build(words: Iterable<String>): CustomWordIndex {
            val exact = linkedMapOf<String, MutableList<String>>()
            val prefixes = linkedMapOf<String, MutableList<String>>()
            words.forEach { word ->
                val digits = T9EnglishDictionary.t9DigitsForWord(word) ?: return@forEach
                exact.getOrPut(digits) { mutableListOf() } += word
                for (length in 1 until digits.length) {
                    prefixes.getOrPut(digits.take(length)) { mutableListOf() } += word
                }
            }
            return CustomWordIndex(exact, prefixes)
        }
    }
}

class ChineseEnglishAutoLearningSession(
    private val learn: (String) -> Unit,
    private val isAlreadyKnown: (String) -> Boolean
) {
    private val firstCommitByWord = object : LinkedHashMap<String, Unit>(
        MaxPendingWords,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > MaxPendingWords
    }

    @Synchronized
    fun recordLiteralCommit(rawText: String, learningAllowed: Boolean): Boolean {
        if (!learningAllowed) return false
        val word = T9EnglishDictionary.normalizeLearnedWord(rawText) ?: return false
        if (isAlreadyKnown(word)) {
            firstCommitByWord.remove(word)
            return false
        }
        if (firstCommitByWord.remove(word) == null) {
            firstCommitByWord[word] = Unit
            return false
        }
        learn(word)
        return true
    }

    companion object {
        private const val MaxPendingWords = 64
    }
}

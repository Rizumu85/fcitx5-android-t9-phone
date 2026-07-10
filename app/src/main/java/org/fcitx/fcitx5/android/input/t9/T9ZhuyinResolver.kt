/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class T9ZhuyinResolver(
    pinyinSyllables: List<String> = T9PinyinUtils.completePinyinSyllables()
) {
    sealed interface Result {
        data object Empty : Result
        data class Invalid(val rawDigits: String) : Result
        data class Valid(val rawDigits: String) : Result
    }

    private data class Segment(val reading: String, val complete: Boolean)

    private data class Path(val segments: List<Segment>) {
        val display: String = segments.joinToString(" ") { it.reading }
        val completeSegmentCount: Int = segments.count(Segment::complete)
    }

    private val completeDigitCodes: Set<String>
    private val prefixDigitCodes: Set<String>
    private val completeReadings: Set<String>
    private val completeByDigits: Map<String, List<Segment>>
    private val prefixByDigits: Map<String, List<Segment>>
    private val validityCache = object : LinkedHashMap<String, Boolean>(
        MaxCacheEntries,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > MaxCacheEntries
    }
    private val optionCache = object : LinkedHashMap<String, List<String>>(
        MaxOptionCacheEntries,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<String>>?
        ): Boolean = size > MaxOptionCacheEntries
    }

    init {
        val readings = pinyinSyllables
            .mapNotNull(T9ZhuyinSyllableCodec::fromPinyin)
            .distinct()
        completeReadings = readings.toSet()
        completeDigitCodes = readings
            .map(::digitsForReading)
            .filter(String::isNotEmpty)
            .toSet()
        completeByDigits = readings
            .map { Segment(it, complete = true) }
            .groupBy { digitsForReading(it.reading) }
            .filterKeys(String::isNotEmpty)
        val prefixes = readings
            .asSequence()
            .flatMap { reading ->
                (1..reading.length).asSequence().map { length ->
                    Segment(
                        reading = reading.take(length),
                        complete = length == reading.length
                    )
                }
            }
            .distinctBy(Segment::reading)
            .toList()
        prefixByDigits = prefixes
            .groupBy { digitsForReading(it.reading) }
            .filterKeys(String::isNotEmpty)
        prefixDigitCodes = prefixByDigits.keys
    }

    fun resolve(rawDigits: String): Result {
        if (rawDigits.isEmpty()) return Result.Empty
        if (rawDigits.any { it !in '0'..'9' }) return Result.Invalid(rawDigits)
        val valid = validityCache[rawDigits] ?: isValidSequence(rawDigits).also {
            validityCache[rawDigits] = it
        }
        return if (valid) Result.Valid(rawDigits) else Result.Invalid(rawDigits)
    }

    fun readingOptions(rawDigits: String): List<String> {
        if (resolve(rawDigits) !is Result.Valid) return emptyList()
        return optionCache[rawDigits] ?: buildReadingOptions(rawDigits).also {
            optionCache[rawDigits] = it
        }
    }

    fun candidateMatchesReadingOption(candidateReading: String, selectedReading: String): Boolean {
        val candidateSegments = normalizeCandidateReading(candidateReading)
            .split(' ')
            .filter(String::isNotEmpty)
        val selectedSegments = normalizeCandidateReading(selectedReading)
            .split(' ')
            .filter(String::isNotEmpty)
        if (candidateSegments.size != selectedSegments.size || selectedSegments.isEmpty()) return false
        if (selectedSegments.dropLast(1).indices.any { index ->
                selectedSegments[index] != candidateSegments[index]
            }
        ) {
            return false
        }
        val selectedFinal = selectedSegments.last()
        val candidateFinal = candidateSegments.last()
        return if (selectedFinal in completeReadings) {
            candidateFinal == selectedFinal
        } else {
            candidateFinal.startsWith(selectedFinal)
        }
    }

    private fun isValidSequence(rawDigits: String): Boolean {
        // Product decision: normal Zhuyin T9 predicts Hanzi directly. Validate only whether a
        // legal segmentation exists; enumerating every reading path created a noisy parallel UI.
        val memo = arrayOfNulls<Boolean>(rawDigits.length + 1)
        fun isValidFrom(start: Int): Boolean {
            memo[start]?.let { return it }
            val maxEnd = minOf(rawDigits.length, start + MaxDigitsPerSyllable)
            for (end in (start + 1)..maxEnd) {
                val code = rawDigits.substring(start, end)
                if (end == rawDigits.length) {
                    if (code in prefixDigitCodes) return true.also { memo[start] = it }
                } else if (code in completeDigitCodes && isValidFrom(end)) {
                    return true.also { memo[start] = it }
                }
            }
            return false.also { memo[start] = it }
        }
        return isValidFrom(0)
    }

    private fun buildReadingOptions(rawDigits: String): List<String> {
        // Keep enumeration separate from resolve() so validation stays cheap for callers that do
        // not render the row; the composition owner invokes this once per raw-code mutation.
        val memo = HashMap<Int, List<Path>>()
        fun pathsFrom(start: Int): List<Path> {
            memo[start]?.let { return it }
            val paths = ArrayList<Path>()
            val maxEnd = minOf(rawDigits.length, start + MaxDigitsPerSyllable)
            for (end in (start + 1)..maxEnd) {
                val code = rawDigits.substring(start, end)
                if (end == rawDigits.length) {
                    prefixByDigits[code].orEmpty().forEach { segment ->
                        paths += Path(listOf(segment))
                    }
                } else {
                    completeByDigits[code].orEmpty().forEach { segment ->
                        pathsFrom(end).forEach { suffix ->
                            paths += Path(listOf(segment) + suffix.segments)
                        }
                    }
                }
                if (paths.size >= MaxReadingOptions * PathSearchMultiplier) break
            }
            return paths
                .distinctBy(Path::display)
                .sortedWith(
                    compareBy<Path> { it.segments.size }
                        .thenByDescending(Path::completeSegmentCount)
                        .thenBy(Path::display)
                )
                .take(MaxReadingOptions)
                .also { memo[start] = it }
        }
        return pathsFrom(0).map(Path::display)
    }

    companion object {
        private const val MaxDigitsPerSyllable = 3
        private const val MaxCacheEntries = 128
        private const val MaxOptionCacheEntries = 64
        private const val MaxReadingOptions = 32
        private const val PathSearchMultiplier = 4

        fun isZhuyinSymbol(char: Char): Boolean =
            char in '\u3105'..'\u312F' || char in '\u31A0'..'\u31BF'

        fun digitsForReading(reading: String): String = buildString {
            reading.forEach { char -> digitForSymbol(char)?.let(::append) }
        }

        fun normalizeCandidateReading(reading: String): String =
            reading
                .trim()
                .split(Regex("[\\s']+"))
                .map { segment -> segment.filter(::isZhuyinSymbol) }
                .filter(String::isNotEmpty)
                .joinToString(" ")

        fun candidateReadingMatches(rawDigits: String, reading: String): Boolean {
            val normalized = normalizeCandidateReading(reading)
            return normalized.isNotEmpty() && digitsForReading(normalized).startsWith(rawDigits)
        }

        fun digitForSymbol(char: Char): Char? = when (char) {
            in 'ㄧ'..'ㄩ' -> '0'
            in 'ㄅ'..'ㄈ' -> '1'
            in 'ㄉ'..'ㄌ' -> '2'
            in 'ㄍ'..'ㄏ' -> '3'
            in 'ㄐ'..'ㄒ' -> '4'
            in 'ㄓ'..'ㄖ' -> '5'
            in 'ㄗ'..'ㄙ' -> '6'
            in 'ㄚ'..'ㄝ' -> '7'
            in 'ㄞ'..'ㄡ' -> '8'
            in 'ㄢ'..'ㄦ' -> '9'
            else -> null
        }
    }
}

internal object T9ZhuyinSyllableCodec {
    private val initials = linkedMapOf(
        "zh" to "ㄓ",
        "ch" to "ㄔ",
        "sh" to "ㄕ",
        "b" to "ㄅ",
        "p" to "ㄆ",
        "m" to "ㄇ",
        "f" to "ㄈ",
        "d" to "ㄉ",
        "t" to "ㄊ",
        "n" to "ㄋ",
        "l" to "ㄌ",
        "g" to "ㄍ",
        "k" to "ㄎ",
        "h" to "ㄏ",
        "j" to "ㄐ",
        "q" to "ㄑ",
        "x" to "ㄒ",
        "r" to "ㄖ",
        "z" to "ㄗ",
        "c" to "ㄘ",
        "s" to "ㄙ"
    )
    private val finals = mapOf(
        "a" to "ㄚ",
        "o" to "ㄛ",
        "e" to "ㄜ",
        "eh" to "ㄝ",
        "ai" to "ㄞ",
        "ei" to "ㄟ",
        "ao" to "ㄠ",
        "ou" to "ㄡ",
        "an" to "ㄢ",
        "en" to "ㄣ",
        "ang" to "ㄤ",
        "eng" to "ㄥ",
        "er" to "ㄦ",
        "i" to "ㄧ",
        "ia" to "ㄧㄚ",
        "io" to "ㄧㄛ",
        "ie" to "ㄧㄝ",
        "iai" to "ㄧㄞ",
        "iao" to "ㄧㄠ",
        "iou" to "ㄧㄡ",
        "ian" to "ㄧㄢ",
        "in" to "ㄧㄣ",
        "iang" to "ㄧㄤ",
        "ing" to "ㄧㄥ",
        "u" to "ㄨ",
        "ua" to "ㄨㄚ",
        "uo" to "ㄨㄛ",
        "uai" to "ㄨㄞ",
        "uei" to "ㄨㄟ",
        "uan" to "ㄨㄢ",
        "uen" to "ㄨㄣ",
        "uang" to "ㄨㄤ",
        "ueng" to "ㄨㄥ",
        "v" to "ㄩ",
        "ve" to "ㄩㄝ",
        "van" to "ㄩㄢ",
        "vn" to "ㄩㄣ",
        "veng" to "ㄩㄥ"
    )
    private val apicalSyllables = mapOf(
        "zhi" to "ㄓ",
        "chi" to "ㄔ",
        "shi" to "ㄕ",
        "ri" to "ㄖ",
        "zi" to "ㄗ",
        "ci" to "ㄘ",
        "si" to "ㄙ"
    )

    fun fromPinyin(source: String): String? {
        val normalized = source.lowercase().replace('ü', 'v')
        apicalSyllables[normalized]?.let { return it }
        val canonical = normalizeZeroInitial(normalized)
        val initial = initials.keys.firstOrNull(canonical::startsWith)
        var final = if (initial == null) canonical else canonical.removePrefix(initial)
        if (initial in setOf("j", "q", "x") && final.startsWith('u')) {
            final = "v" + final.drop(1)
        }
        final = when (final) {
            "iu" -> "iou"
            "ui" -> "uei"
            "un" -> "uen"
            "ong" -> "ueng"
            "iong" -> "veng"
            else -> final
        }
        val finalReading = finals[final] ?: return null
        return initials[initial].orEmpty() + finalReading
    }

    private fun normalizeZeroInitial(pinyin: String): String = when (pinyin) {
        "yi" -> "i"
        "yin" -> "in"
        "ying" -> "ing"
        "ya" -> "ia"
        "yo" -> "io"
        "ye" -> "ie"
        "yao" -> "iao"
        "you" -> "iou"
        "yan" -> "ian"
        "yang" -> "iang"
        "yong" -> "iong"
        "yu" -> "v"
        "yue" -> "ve"
        "yuan" -> "van"
        "yun" -> "vn"
        "wu" -> "u"
        "wa" -> "ua"
        "wo" -> "uo"
        "wai" -> "uai"
        "wei" -> "uei"
        "wan" -> "uan"
        "wen" -> "uen"
        "wang" -> "uang"
        "weng" -> "ueng"
        else -> pinyin
    }
}

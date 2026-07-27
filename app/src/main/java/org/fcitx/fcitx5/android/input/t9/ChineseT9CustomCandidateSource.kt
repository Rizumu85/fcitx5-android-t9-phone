/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class ChineseT9CustomCandidateSource(
    private val phraseDictionary: () -> ChineseT9CustomPhraseDictionary = {
        ChineseT9CustomPhraseDictionary.Shared
    },
    private val englishDictionaries: () -> EnglishCustomDictionaryCoordinator = {
        EnglishCustomDictionaryCoordinator.Shared
    }
) {
    internal constructor(
        phrases: ChineseT9CustomPhraseDictionary,
        english: EnglishCustomDictionaryCoordinator
    ) : this(
        phraseDictionary = { phrases },
        englishDictionaries = { english }
    )

    private val directCommitTextByIndex = mutableMapOf<Int, String>()

    fun buildCustomCandidates(
        snapshot: ChineseT9InputSnapshot,
        englishCandidatesEnabled: Boolean
    ): T9PagedCandidates? {
        directCommitTextByIndex.clear()
        if (snapshot.scheme != ChineseT9Scheme.PINYIN || snapshot.digitSequence.isEmpty()) {
            return null
        }
        val candidates = buildList {
            phraseDictionary().candidatesForDigits(snapshot.digitSequence, CandidateLimit)
                .forEach { entry ->
                    add(
                        FcitxEvent.Candidate(
                            label = "",
                            text = entry.text,
                            comment = entry.pinyin.replace('\'', ' ')
                        )
                    )
                }
            if (englishCandidatesEnabled) {
                englishDictionaries().candidatesForChinese(
                    snapshot.digitSequence,
                    CandidateLimit - size
                )
                    .forEach { word ->
                        add(FcitxEvent.Candidate(label = "", text = word, comment = ""))
                    }
            }
        }.distinctBy(FcitxEvent.Candidate::text)
        if (candidates.isEmpty()) return null
        val originalIndices = IntArray(candidates.size) { offset ->
            DirectCommitIndexStart + offset
        }
        candidates.forEachIndexed { offset, candidate ->
            directCommitTextByIndex[DirectCommitIndexStart + offset] = candidate.text
        }
        return T9PagedCandidates(
            data = FcitxEvent.PagedCandidateEvent.Data(
                candidates = candidates.toTypedArray(),
                cursorIndex = 0,
                layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
                hasPrev = false,
                hasNext = false
            ),
            originalIndices = originalIndices
        )
    }

    fun mergeWithEngine(
        engine: T9PagedCandidates,
        custom: T9PagedCandidates?,
        englishCandidatesEnabled: Boolean
    ): T9PagedCandidates {
        val customIndexed = custom?.indexedCandidates().orEmpty()
        val customTexts = customIndexed.mapTo(HashSet()) { it.value.text }
        val engineIndexed = engine.indexedCandidates().filter { (_, candidate) ->
            candidate.text !in customTexts &&
                (englishCandidatesEnabled || !candidate.text.isPureLatinCandidate())
        }
        if (customIndexed.isEmpty() && engineIndexed.size == engine.data.candidates.size) {
            return engine
        }
        val merged = customIndexed + engineIndexed
        val selectedOriginalIndex = engine.originalIndices.getOrNull(engine.data.cursorIndex)
        val cursor = if (customIndexed.isNotEmpty()) {
            0
        } else {
            merged.indexOfFirst { it.index == selectedOriginalIndex }
                .takeIf { it >= 0 }
                ?: merged.indices.firstOrNull()
                ?: -1
        }
        return T9PagedCandidates(
            data = engine.data.copy(
                candidates = merged.map { it.value }.toTypedArray(),
                cursorIndex = cursor
            ),
            originalIndices = merged.map { it.index }.toIntArray()
        )
    }

    fun directCommitText(originalIndex: Int, candidateText: String): String? =
        directCommitTextByIndex[originalIndex]?.takeIf { it == candidateText }

    fun isDirectCommitIndex(originalIndex: Int): Boolean =
        originalIndex in DirectCommitIndexStart..DirectCommitIndexEnd

    fun reset() {
        directCommitTextByIndex.clear()
    }

    companion object {
        private const val CandidateLimit = 32
        private const val DirectCommitIndexStart = Int.MIN_VALUE
        private const val DirectCommitIndexEnd = DirectCommitIndexStart + CandidateLimit - 1

        internal fun String.isPureLatinCandidate(): Boolean {
            var hasLetter = false
            forEach { char ->
                when {
                    char in 'a'..'z' || char in 'A'..'Z' -> hasLetter = true
                    char == '\'' || char == '-' -> Unit
                    else -> return false
                }
            }
            return hasLetter
        }
    }
}

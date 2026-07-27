/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9CustomDictionariesTest {

    @Test
    fun phraseDictionaryNormalizesPinyinAndIndexesExactAndPrefixDigits() {
        val dictionary = phraseDictionary()
        dictionary.replaceEntries(
            listOf(
                ChineseT9CustomPhrase("你好", "Ni Hao"),
                ChineseT9CustomPhrase("您好", "nin'hao"),
                ChineseT9CustomPhrase("plain", "plain")
            )
        )

        assertEquals(
            listOf(ChineseT9CustomPhrase("你好", "ni'hao")),
            dictionary.candidatesForDigits("64426", 10)
        )
        assertEquals(
            listOf("你好", "您好"),
            dictionary.candidatesForDigits("64", 10).map(ChineseT9CustomPhrase::text)
        )
        assertNull(ChineseT9CustomPhrase.create("plain", "plain"))
    }

    @Test
    fun sharingPresentsOneUnionButUnsharedLearningKeepsItsOrigin() {
        val smart = T9EnglishDictionary(null)
        val chinese = chineseEnglishDictionary()
        var shared = false
        val coordinator = EnglishCustomDictionaryCoordinator(smart, chinese) { shared }

        smart.learn("hello")
        chinese.learn("gelato")

        assertEquals(listOf("hello"), coordinator.words(EnglishCustomDictionaryScope.SMART_ENGLISH))
        assertEquals(listOf("gelato"), coordinator.words(EnglishCustomDictionaryScope.CHINESE))

        shared = true
        assertEquals(
            listOf("gelato", "hello"),
            coordinator.words(EnglishCustomDictionaryScope.CHINESE)
        )
        assertEquals(listOf("gelato"), coordinator.additionalCandidatesForSmartEnglish("435", 10))
        assertEquals(listOf("gelato", "hello"), coordinator.candidatesForChinese("435", 10))
    }

    @Test
    fun sharedChineseWordsJoinPredictiveEnglishWithoutReplacingItsFirstCandidate() {
        val smart = T9EnglishDictionary(null).apply { learn("hello") }
        val chinese = chineseEnglishDictionary().apply { learn("gelato") }
        val coordinator = EnglishCustomDictionaryCoordinator(smart, chinese) { true }
        val engine = EnglishSuggestionEngine(
            dictionary = smart,
            predictionDictionary = SmartEnglishPredictionDictionary(emptyMap()),
            customDictionaryCoordinator = coordinator
        )

        assertEquals(
            listOf("hello", "gelato"),
            engine.candidatesForDigits("435", 10).take(2)
        )
    }

    @Test
    fun secondLiteralCommitLearnsOnceAndRespectsLearningPolicy() {
        val learned = mutableListOf<String>()
        val session = ChineseEnglishAutoLearningSession(
            learn = learned::add,
            isAlreadyKnown = { it in learned }
        )

        assertFalse(session.recordLiteralCommit("hello", learningAllowed = true))
        assertTrue(session.recordLiteralCommit("HELLO", learningAllowed = true))
        assertEquals(listOf("hello"), learned)
        assertFalse(session.recordLiteralCommit("secret", learningAllowed = false))
        assertFalse(session.recordLiteralCommit("secret", learningAllowed = true))
        assertFalse(session.recordLiteralCommit("1234", learningAllowed = true))
    }

    @Test
    fun customSourcePrependsDirectCandidatesAndFiltersEngineEnglishWhenDisabled() {
        val phraseDictionary = phraseDictionary().apply {
            replaceEntries(listOf(ChineseT9CustomPhrase("你好", "nihao")))
        }
        val smart = T9EnglishDictionary(null)
        val chinese = chineseEnglishDictionary().apply { learn("niche") }
        val source = ChineseT9CustomCandidateSource(
            phrases = phraseDictionary,
            english = EnglishCustomDictionaryCoordinator(smart, chinese) { false }
        )
        val snapshot = chineseSnapshot("64426")
        val custom = source.buildCustomCandidates(snapshot, englishCandidatesEnabled = true)
        val engine = T9PagedCandidates.passthrough(
            FcitxEvent.PagedCandidateEvent.Data(
                candidates = arrayOf(
                    FcitxEvent.Candidate("", "你", "ni"),
                    FcitxEvent.Candidate("", "night", "")
                ),
                cursorIndex = 0,
                layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
                hasPrev = false,
                hasNext = true
            )
        )

        val enabled = source.mergeWithEngine(engine, custom, englishCandidatesEnabled = true)
        assertEquals(listOf("你好", "你", "night"), enabled.data.candidates.map { it.text })
        assertEquals(
            "你好",
            source.directCommitText(enabled.originalIndices.first(), candidateText = "你好")
        )

        val disabledCustom = source.buildCustomCandidates(snapshot, englishCandidatesEnabled = false)
        val disabled = source.mergeWithEngine(engine, disabledCustom, englishCandidatesEnabled = false)
        assertEquals(listOf("你好", "你"), disabled.data.candidates.map { it.text })
    }

    private fun phraseDictionary() = ChineseT9CustomPhraseDictionary(
        T9DictionaryPersistence(
            file = null,
            defaultValue = emptyList(),
            decode = { emptyList() },
            encode = { "" }
        )
    )

    private fun chineseEnglishDictionary() = ChineseT9EnglishDictionary(
        T9DictionaryPersistence(
            file = null,
            defaultValue = emptySet(),
            decode = { emptySet() },
            encode = { "" }
        )
    )

    private fun chineseSnapshot(digits: String) = ChineseT9InputSnapshot(
        rawSequence = digits,
        digitSequence = digits,
        currentSegment = digits,
        fullComposition = digits,
        model = T9CompositionModel(unresolvedDigits = digits, rawPreedit = digits),
        keyCount = digits.length,
        filterPrefixes = emptyList(),
        hasPendingPinyinSelection = false,
        sessionRevision = 1L,
        scheme = ChineseT9Scheme.PINYIN
    )
}

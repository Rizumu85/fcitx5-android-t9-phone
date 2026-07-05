/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEnglishT9ControllerTest {

    @Test
    fun appendingDigitBuildsPagedCandidatesAndRefreshesUi() {
        val host = FakeHost()
        val controller = host.controller()

        controller.appendDigit(4)

        val paged = controller.paged()
        assertNotNull(paged)
        assertEquals("I", paged!!.candidates.single().text)
        assertEquals(1, host.refreshCount)
    }

    @Test
    fun commitCandidateAppliesShiftOnceAndResetsDigits() {
        val host = FakeHost()
        val controller = host.controller()
        controller.toggleShiftOnce()
        controller.appendDigit(2)

        assertTrue(controller.commitCandidate())

        assertEquals(listOf("A "), host.committedTexts)
        assertFalse(controller.hasDigits)
        assertEquals("abc", controller.caseLabel)
    }

    @Test
    fun inactiveControllerDoesNotExposeCandidates() {
        val host = FakeHost(active = false)
        val controller = host.controller()
        controller.appendDigit(4)

        assertEquals(null, controller.paged())
        assertFalse(controller.commitCandidate())
    }

    @Test
    fun presentationUsesTypedPreview() {
        val host = FakeHost()
        val controller = host.controller()
        controller.appendDigit(4)

        val presentation = controller.presentation {
            FormattedText(arrayOf(it), intArrayOf(TextFormatFlag.NoFlag.flag), -1)
        }

        assertEquals("I", presentation?.topReading?.toString())
    }

    @Test
    fun loadingDictionarySuppressesEmptyFallbackUi() {
        val host = FakeHost(dictionaryReady = false)
        val controller = host.controller()

        controller.appendDigit(9)

        assertEquals(null, controller.paged())
        assertEquals(null, controller.presentation {
            FormattedText(arrayOf(it), intArrayOf(TextFormatFlag.NoFlag.flag), -1)
        })
    }

    @Test
    fun loadingDictionaryStillShowsEssentialWords() {
        val host = FakeHost(dictionaryReady = false)
        val controller = host.controller()

        controller.appendDigit(4)

        assertEquals("I", controller.paged()?.candidates?.single()?.text)
    }

    @Test
    fun loadingDictionaryUsesAvailablePrefixCandidatesForPreview() {
        val host = FakeHost(dictionaryReady = false)
        val controller = host.controller()

        listOf(4, 3, 5).forEach(controller::appendDigit)

        assertEquals("hello", controller.paged()?.candidates?.first()?.text)
        assertEquals("hel", controller.presentation {
            FormattedText(arrayOf(it), intArrayOf(TextFormatFlag.NoFlag.flag), -1)
        }?.topReading?.toString())
    }

    @Test
    fun learningFlushesCompletedWordsWhenEnabled() {
        val host = FakeHost(learn = true)
        val controller = host.controller()

        "Rizum ".forEach(controller::recordLearningChar)

        assertEquals(listOf("rizum"), host.learnedWords)
    }

    @Test
    fun committedSmartWordShowsNextWordPredictions() {
        val host = FakeHost()
        val controller = host.controller()
        listOf(4, 6, 6).forEach(controller::appendDigit)

        assertTrue(controller.commitCandidate())

        assertEquals(listOf("good "), host.committedTexts)
        assertEquals("morning", controller.paged()?.candidates?.first()?.text)
        val presentation = controller.presentation {
            FormattedText(arrayOf(it), intArrayOf(TextFormatFlag.NoFlag.flag), -1)
        }
        assertEquals("morning", presentation?.topReading?.toString())
        assertTrue(presentation?.reserveTopReadingRow == true)
    }

    @Test
    fun predictionCandidateCanBeCommittedAndContinuesPrediction() {
        val host = FakeHost()
        val controller = host.controller()
        listOf(4, 6, 6).forEach(controller::appendDigit)
        controller.commitCandidate()

        assertTrue(controller.commitCandidate())

        assertEquals(listOf("good ", "morning "), host.committedTexts)
        assertEquals("sunshine", controller.paged()?.candidates?.first()?.text)
    }

    @Test
    fun typedCandidatesAreRerankedByLearnedPreviousWordPair() {
        val host = FakeHost()
        val controller = host.controller()
        listOf(4, 6, 6).forEach(controller::appendDigit)
        assertTrue(controller.commitCandidate())

        "6676464".forEach { controller.appendDigit(it.digitToInt()) }

        assertEquals("morning", controller.paged()?.candidates?.first()?.text)
        assertTrue(controller.commitCandidate())
        assertEquals(listOf("good ", "morning "), host.committedTexts)
    }

    @Test
    fun typedCandidatesKeepDictionaryOrderWhenPairDoesNotMatchDigits() {
        val host = FakeHost()
        val controller = host.controller()
        listOf(4, 6, 6).forEach(controller::appendDigit)
        assertTrue(controller.commitCandidate())

        listOf(4, 6, 6).forEach(controller::appendDigit)

        assertEquals(listOf("good", "home"), controller.paged()?.candidates?.map { it.text })
    }

    @Test
    fun predictionBackspaceHidesPredictionWithoutClearingContext() {
        val host = FakeHost()
        val controller = host.controller()
        listOf(4, 6, 6).forEach(controller::appendDigit)
        controller.commitCandidate()

        assertTrue(controller.backspace())

        assertEquals(null, controller.paged())
        assertFalse(controller.backspace())
    }

    @Test
    fun learningFlushRecordsNextWordPairsWhenEnabled() {
        val host = FakeHost(learn = true)
        val controller = host.controller()

        "Good Morning ".forEach(controller::recordLearningChar)

        assertEquals(listOf("good", "morning"), host.learnedWords)
        assertEquals(listOf("good" to "morning"), host.learnedPredictionPairs)
    }

    private class FakeHost(
        var active: Boolean = true,
        var learn: Boolean = false,
        var dictionaryReady: Boolean = true
    ) {
        private val candidates = mapOf(
            "2" to listOf("a"),
            "4" to listOf("I"),
            "435" to listOf("hello", "help"),
            "466" to listOf("good", "home"),
            "43556" to listOf("hello", "gekko"),
            "6676464" to listOf("mopping", "morning")
        )
        private val predictions = mapOf(
            "good" to listOf("morning", "night"),
            "morning" to listOf("sunshine")
        )
        val committedTexts = mutableListOf<String>()
        val learnedWords = mutableListOf<String>()
        val learnedPredictionPairs = mutableListOf<Pair<String, String>>()
        var refreshCount = 0

        fun controller(): SmartEnglishT9Controller = SmartEnglishT9Controller(
            candidateProvider = { digits, limit -> candidates[digits].orEmpty().take(limit) },
            predictionProvider = { words, limit -> predictions[words.last()].orEmpty().take(limit) },
            learnWord = { learnedWords += it },
            learnPredictionPair = { previous, next -> learnedPredictionPairs += previous to next },
            dictionaryReady = { dictionaryReady },
            candidateLimit = 10,
            noMatchText = "No match",
            isActive = { active },
            shouldLearnWords = { learn },
            commitText = { committedTexts += it },
            refreshUi = { refreshCount += 1 }
        )
    }
}

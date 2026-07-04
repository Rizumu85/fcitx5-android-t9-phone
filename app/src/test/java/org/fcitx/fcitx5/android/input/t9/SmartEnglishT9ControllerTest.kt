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

        assertEquals(listOf("A"), host.committedTexts)
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
    fun learningFlushesCompletedWordsWhenEnabled() {
        val host = FakeHost(learn = true)
        val controller = host.controller()

        "Rizum ".forEach(controller::recordLearningChar)

        assertEquals(listOf("rizum"), host.learnedWords)
    }

    private class FakeHost(
        var active: Boolean = true,
        var learn: Boolean = false
    ) {
        private val candidates = mapOf(
            "2" to listOf("a"),
            "4" to listOf("I"),
            "43556" to listOf("hello", "gekko")
        )
        val committedTexts = mutableListOf<String>()
        val learnedWords = mutableListOf<String>()
        var refreshCount = 0

        fun controller(): SmartEnglishT9Controller = SmartEnglishT9Controller(
            candidateProvider = { digits, limit -> candidates[digits].orEmpty().take(limit) },
            learnWord = { learnedWords += it },
            candidateLimit = 10,
            noMatchText = "No match",
            isActive = { active },
            shouldLearnWords = { learn },
            commitText = { committedTexts += it },
            refreshUi = { refreshCount += 1 }
        )
    }
}

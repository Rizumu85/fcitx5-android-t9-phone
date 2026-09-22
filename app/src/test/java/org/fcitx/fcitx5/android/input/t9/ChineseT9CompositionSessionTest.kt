/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.*
import org.junit.Test

class ChineseT9CompositionSessionTest {
    @Test fun consecutiveSelectionsPreserveSeparatorsAndSuffix() {
        val s = session("64'426'62")
        assertEquals("ni'426'62", s.selectPinyin("ni")!!.input)
        assertEquals("426", s.currentSegment())
        assertEquals("ni'hao'62", s.selectPinyin("hao")!!.input)
        assertEquals("64'426'62", s.rawSequence())
        assertEquals("62", s.currentSegment())
        assertEquals("ni'hao'ma'", s.selectPinyin("ma")!!.input)
        assertEquals("", s.currentSegment())
    }

    @Test fun reopeningThenTypingDoesNotDuplicateResolvedDigits() {
        val s = session("64426")
        s.selectPinyin("ni")
        s.selectPinyin("hao")
        assertEquals("ni'426", s.popLastResolvedSegment()!!.input)
        s.appendDigit('3')
        assertEquals("644263", s.rawSequence())
        assertEquals("ni'4263", s.projection().input)
        assertEquals("4263", s.currentSegment())
    }

    @Test fun selectingInitialBeforeSeparatorKeepsUnselectedDigits() {
        val s = session("64'426")
        assertEquals("n'4'426", s.selectPinyin("n")!!.input)
        assertEquals("4", s.currentSegment())
        assertEquals("n'i'426", s.selectPinyin("i")!!.input)
        assertEquals("426", s.currentSegment())
    }

    @Test fun partialCommitPreservesUnreadChoicesAndSeparators() {
        val s = session("64'426'62")
        s.selectPinyin("ni")
        s.selectPinyin("hao")
        assertEquals("426'62", s.consumeSelectedCandidateReading(listOf("ni")))
        assertEquals(listOf(T9ResolvedSegment("hao", "426")), s.resolvedSegments)
        assertEquals("hao'62", s.projection().input)
    }

    @Test fun partialCommitConsumesExplicitInitialByItsSourceSpan() {
        val s = session("44")
        s.selectPinyin("h")
        s.selectPinyin("g")
        assertEquals("4", s.consumeSelectedCandidateReading(listOf("hao")))
        assertEquals("g'", s.projection().input)
        assertEquals("", s.consumeSelectedCandidateReading(listOf("ge")))
        assertFalse(s.hasState())
    }

    @Test fun wrongSameCodeReadingCannotConsumeSelectedSource() {
        val s = session("43")
        s.selectPinyin("ge")
        assertNull(s.consumeSelectedCandidateReading(listOf("he")))
        assertEquals("43", s.rawSequence())
    }

    @Test fun typingKeepsPriorCommandValidButClearInvalidatesIt() {
        val s = session("64")
        val choice = s.selectPinyin("ni")!!
        s.appendDigit('4')
        assertTrue(s.accepts(choice))
        assertEquals("ni'", choice.input)
        s.clear()
        "64".forEach(s::appendDigit)
        s.selectPinyin("ni")
        assertFalse(s.accepts(choice))
    }

    @Test fun repeatedSyllablesReopenTheLastSourceSpan() {
        val s = session("64'64'64")
        repeat(3) { s.selectPinyin("ni")!! }
        assertEquals("ni'ni'64", s.popLastResolvedSegment()!!.input)
        assertEquals("ni'64'64", s.popLastResolvedSegment()!!.input)
        assertEquals("64'64'64", s.popLastResolvedSegment()!!.input)
        assertNull(s.popLastResolvedSegment())
    }

    @Test fun selectionsAndReopeningConserveRawInputAcrossManySegmentations() {
        for (raw in listOf("6442662", "64'426'62", "64'42662", "64426'62", "64''426'62")) {
            val s = session(raw)
            for (reading in listOf("ni", "hao", "ma")) {
                assertNotNull(s.selectPinyin(reading))
                assertEquals(raw, s.rawSequence())
            }
            repeat(3) {
                assertNotNull(s.popLastResolvedSegment())
                assertEquals(raw, s.rawSequence())
            }
        }
    }

    @Test fun ordinaryPartialCandidateKeepsTheUnconsumedDigits() {
        val s = session("6426")
        assertEquals("26", s.consumeSelectedCandidateReading(listOf("ni")))
        assertEquals("26", s.unresolvedDigits)
    }

    private fun session(raw: String) = ChineseT9CompositionSession().apply { replace(raw) }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9CompositionSessionTest {

    @Test
    fun selectPinyinUpdatesModelAndReturnsEngineReplacementRequest() {
        val session = ChineseT9CompositionSession()
        session.appendDigit('6')
        session.appendDigit('4')

        val request = session.selectPinyin("ni")

        assertNotNull(request)
        request!!
        assertEquals("64", request.originalSegment)
        assertEquals("", request.remainingDigits)
        assertEquals(T9ResolvedSegment("ni", "64"), request.selectedSegment)
        assertFalse(request.consumeExplicitSeparator)
        assertEquals(listOf(T9ResolvedSegment("ni", "64")), session.resolvedSegments)
        assertEquals(T9PendingSelection(T9ResolvedSegment("ni", "64"), ""), session.pendingSelection)
    }

    @Test
    fun engineBackedSelectionClearsMatchingPendingSelection() {
        val session = ChineseT9CompositionSession()
        session.appendDigit('6')
        session.appendDigit('4')
        val request = session.selectPinyin("ni")!!

        session.markSelectionEngineBacked(request.selectedSegment, request.remainingDigits)

        assertNull(session.pendingSelection)
        assertTrue(session.resolvedSegments.single().engineBacked)
    }

    @Test
    fun popLastResolvedSegmentRestoresSourceDigits() {
        val session = ChineseT9CompositionSession()
        session.appendDigit('6')
        session.appendDigit('4')
        val request = session.selectPinyin("ni")!!
        session.markSelectionEngineBacked(request.selectedSegment, request.remainingDigits)

        val popped = session.popLastResolvedSegment()

        assertNotNull(popped)
        popped!!
        assertEquals(T9ResolvedSegment("ni", "64", engineBacked = true), popped.segment)
        assertEquals("", popped.previousUnresolved)
        assertEquals("64", popped.fallbackRawPreedit)
        assertFalse(session.hasResolvedSegments)
        assertEquals("64", session.unresolvedDigits)
    }

    @Test
    fun selectedCandidateReadingConsumesMatchingDigits() {
        val session = ChineseT9CompositionSession()
        listOf('6', '4', '2', '6').forEach(session::appendDigit)

        assertTrue(session.consumeSelectedCandidateReading(listOf("ni")))

        assertEquals("26", session.unresolvedDigits)
        assertEquals("26", session.rawPreedit)
    }

    @Test
    fun revisionChangesWhenPresentationStateChanges() {
        val session = ChineseT9CompositionSession()
        val initial = session.revision

        session.appendDigit('6')
        val afterDigit = session.revision
        session.syncFromPreedit("6")

        assertTrue(afterDigit > initial)
        assertEquals(afterDigit, session.revision)

        session.selectPinyin("m")!!

        assertTrue(session.revision > afterDigit)
    }

    @Test
    fun emptyDisplayPreeditDoesNotClearAuthoritativeTypedDigits() {
        val session = ChineseT9CompositionSession()
        session.appendDigit('6')
        session.syncFromPreedit("o")

        session.syncFromPreedit("")

        assertEquals("6", session.rawSequence())
        assertEquals("6", session.currentSegment { raw, _ -> raw })
        assertEquals(1, session.keyCount())
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9RimeBridgeTest {

    @Test
    fun mirrorPinyinSelectionMarksSegmentEngineBackedOnSuccessfulReplace() = runBlocking {
        val session = ChineseT9CompositionSession()
        session.appendDigit('6')
        session.appendDigit('4')
        val request = session.selectPinyin("ni")!!
        val io = FakeRimeIo(input = "64")

        val replaced = ChineseT9RimeBridge(session, io).mirrorPinyinSelection(request)

        assertTrue(replaced)
        assertEquals(listOf(ReplaceCall(start = 0, length = 2, text = "ni'", caretPos = 3)), io.replacements)
        assertEquals(T9ResolvedSegment("ni", "64", engineBacked = true), session.resolvedSegments.single())
        assertNull(session.pendingSelection)
    }

    @Test
    fun mirrorPinyinSelectionClearsPendingSelectionWhenReplaceFails() = runBlocking {
        val session = ChineseT9CompositionSession()
        session.appendDigit('6')
        session.appendDigit('4')
        val request = session.selectPinyin("ni")!!
        val io = FakeRimeIo(input = "99", replaceResult = false)

        val replaced = ChineseT9RimeBridge(session, io).mirrorPinyinSelection(request)

        assertFalse(replaced)
        assertEquals(T9ResolvedSegment("ni", "64", engineBacked = false), session.resolvedSegments.single())
        assertNull(session.pendingSelection)
    }

    @Test
    fun restoreResolvedSegmentFallsBackToReplayWhenReplaceFails() = runBlocking {
        val session = ChineseT9CompositionSession()
        session.appendDigit('6')
        session.appendDigit('4')
        val request = session.selectPinyin("ni")!!
        val io = FakeRimeIo(input = "ni'", replaceResult = false)
        val bridge = ChineseT9RimeBridge(session, io)
        bridge.mirrorPinyinSelection(request)

        val restored = bridge.restoreResolvedSegment(
            segment = T9ResolvedSegment("ni", "64", engineBacked = true),
            previousUnresolved = "",
            fallbackRawPreedit = "64",
            candidatePagingMode = 1
        )

        assertFalse(restored)
        assertEquals(listOf(1), io.candidatePagingModes)
        assertEquals(1, io.resetCount)
        assertEquals(listOf('6', '4'), io.sentKeys)
        assertEquals(T9ResolvedSegment("ni", "64", engineBacked = false), session.resolvedSegments.single())
        assertEquals("64", session.rawPreedit)
    }

    data class ReplaceCall(
        val start: Int,
        val length: Int,
        val text: String,
        val caretPos: Int
    )

    private class FakeRimeIo(
        private val input: String,
        private val replaceResult: Boolean = true
    ) : ChineseT9RimeBridge.RimeIo {
        val replacements = mutableListOf<ReplaceCall>()
        val candidatePagingModes = mutableListOf<Int>()
        val sentKeys = mutableListOf<Char>()
        var resetCount = 0

        override suspend fun getInput(): String = input

        override suspend fun replaceInput(
            start: Int,
            length: Int,
            text: String,
            caretPos: Int
        ): Boolean {
            replacements += ReplaceCall(start, length, text, caretPos)
            return replaceResult
        }

        override suspend fun setCandidatePagingMode(mode: Int) {
            candidatePagingModes += mode
        }

        override suspend fun reset() {
            resetCount += 1
        }

        override suspend fun sendKey(char: Char) {
            sentKeys += char
        }
    }
}

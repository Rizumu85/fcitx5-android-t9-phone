/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalHandwritingKeyHandlerTest {
    @Test
    fun deleteUndoesStrokeButFallsThroughWhenCanvasIsEmpty() {
        val fixture = Fixture(hasStrokes = true)

        val handled = fixture.keyDown(KeyEvent.KEYCODE_DEL)

        assertTrue(requireNotNull(handled).handled)
        assertEquals(KeyEvent.KEYCODE_DEL, handled.consumeKeyUp)
        assertEquals(1, fixture.undoCount)

        fixture.hasStrokes = false
        assertNull(
            fixture.keyDown(KeyEvent.KEYCODE_DEL)
        )
    }

    @Test
    fun poundCommitsCandidateWithStrokesAndSendsReturnWhenEmpty() {
        val fixture = Fixture(hasStrokes = true)

        fixture.keyDown(KeyEvent.KEYCODE_POUND)
        assertTrue(
            requireNotNull(
                fixture.keyUp(KeyEvent.KEYCODE_POUND)
            ).handled
        )
        assertEquals(1, fixture.commitCurrentCount)
        assertEquals(0, fixture.returnCount)

        fixture.hasStrokes = false
        fixture.keyDown(KeyEvent.KEYCODE_POUND)
        fixture.keyUp(KeyEvent.KEYCODE_POUND)
        assertEquals(1, fixture.returnCount)
    }

    @Test
    fun longNumberCommitsShortcutOnlyOnceAndShortNumberDoesNothing() {
        val fixture = Fixture(hasCandidates = true)

        fixture.keyDown(KeyEvent.KEYCODE_2)
        fixture.keyUp(KeyEvent.KEYCODE_2)
        assertEquals(emptyList<Int>(), fixture.shortcuts)

        fixture.keyDown(KeyEvent.KEYCODE_2)
        fixture.keyDown(KeyEvent.KEYCODE_2, repeatCount = 1, eventTime = 600L)
        fixture.keyDown(KeyEvent.KEYCODE_2, repeatCount = 2, eventTime = 800L)
        fixture.keyUp(KeyEvent.KEYCODE_2)

        assertEquals(listOf(1), fixture.shortcuts)
    }

    @Test
    fun directionAndConfirmKeysStayInsideHandwritingCandidateFlow() {
        val fixture = Fixture()

        fixture.keyDown(KeyEvent.KEYCODE_DPAD_LEFT)
        fixture.keyDown(KeyEvent.KEYCODE_DPAD_RIGHT)
        fixture.keyDown(KeyEvent.KEYCODE_DPAD_UP)
        fixture.keyDown(KeyEvent.KEYCODE_DPAD_DOWN)
        fixture.keyDown(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(listOf(-1, 1), fixture.moves)
        assertEquals(listOf(-1, 1), fixture.pages)
        assertEquals(1, fixture.commitCurrentCount)
    }

    @Test
    fun confirmRequiresTheRawSelectKeyRatherThanItsInputModeSpaceMapping() {
        val fixture = Fixture()

        fixture.keyDown(KeyEvent.KEYCODE_DPAD_CENTER)
        val mappedSpace = fixture.keyDown(KeyEvent.KEYCODE_SPACE)

        assertEquals(1, fixture.commitCurrentCount)
        assertNull(mappedSpace)
    }

    private class Fixture(
        var hasStrokes: Boolean = false,
        var hasCandidates: Boolean = false
    ) {
        var undoCount = 0
        var commitCurrentCount = 0
        var returnCount = 0
        val shortcuts = mutableListOf<Int>()
        val moves = mutableListOf<Int>()
        val pages = mutableListOf<Int>()

        val handler = PhysicalHandwritingKeyHandler(
            longPressDelayMillis = { 500 },
            hasStrokes = { hasStrokes },
            hasCandidates = { hasCandidates },
            undoStroke = {
                hasStrokes.also { if (it) undoCount++ }
            },
            moveCandidate = { moves += it; true },
            offsetPage = { pages += it; true },
            commitCurrentCandidate = { commitCurrentCount++; true },
            commitShortcut = { shortcuts += it; true },
            sendReturn = { returnCount++ }
        )

        fun keyDown(
            keyCode: Int,
            repeatCount: Int = 0,
            eventTime: Long = 0L
        ) = handler.handleKeyDown(
            keyCode,
            PhysicalHandwritingKeyHandler.KeyInput(
                action = KeyEvent.ACTION_DOWN,
                repeatCount = repeatCount,
                downTime = 0L,
                eventTime = eventTime
            )
        )

        fun keyUp(keyCode: Int) = handler.handleKeyUp(
            keyCode,
            PhysicalHandwritingKeyHandler.KeyInput(
                action = KeyEvent.ACTION_UP,
                repeatCount = 0,
                downTime = 0L,
                eventTime = 0L
            )
        )
    }
}

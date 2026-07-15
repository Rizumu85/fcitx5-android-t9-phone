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
    fun deleteClearsThePendingCharacterThenFallsThroughToEditorDeletion() {
        val fixture = Fixture(hasStrokes = true, hasCandidates = true)

        val handled = fixture.keyDown(KeyEvent.KEYCODE_DEL)

        assertTrue(requireNotNull(handled).handled)
        assertEquals(KeyEvent.KEYCODE_DEL, handled.consumeKeyUp)
        assertEquals(1, fixture.clearCount)
        assertEquals(false, fixture.hasStrokes)
        assertEquals(false, fixture.hasCandidates)

        assertNull(
            fixture.keyDown(KeyEvent.KEYCODE_DEL)
        )
    }

    @Test
    fun deleteDismissesPredictionButStillFallsThroughToEditorDeletion() {
        var predictionVisible = true
        val handler = PhysicalHandwritingKeyHandler(
            longPressDelayMillis = { 500 },
            hasStrokes = { false },
            hasCandidates = { predictionVisible },
            clearPendingCharacter = {
                predictionVisible = false
                false
            },
            moveCandidate = { true },
            offsetPage = { true },
            commitCurrentCandidate = { true },
            commitShortcut = { true },
            performAction = {}
        )

        val result = handler.handleKeyDown(
            KeyEvent.KEYCODE_DEL,
            PhysicalHandwritingKeyHandler.KeyInput(
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                downTime = 0L,
                eventTime = 0L
            )
        )

        assertNull(result)
        assertEquals(false, predictionVisible)
    }

    @Test
    fun poundCommitsCandidateWithStrokesAndPerformsReturnWhenEmpty() {
        val fixture = Fixture(hasStrokes = true)

        fixture.keyDown(KeyEvent.KEYCODE_POUND)
        assertTrue(
            requireNotNull(
                fixture.keyUp(KeyEvent.KEYCODE_POUND)
            ).handled
        )
        assertEquals(1, fixture.commitCurrentCount)
        assertEquals(emptyList<HandwritingAction>(), fixture.actions)

        fixture.hasStrokes = false
        fixture.keyDown(KeyEvent.KEYCODE_POUND)
        fixture.keyUp(KeyEvent.KEYCODE_POUND)
        assertEquals(listOf(HandwritingAction.RETURN), fixture.actions)
    }

    @Test
    fun shortRailShortcutsPerformTheSameActionsAsTheTouchButtons() {
        val fixture = Fixture(hasCandidates = true)

        listOf(
            KeyEvent.KEYCODE_1 to HandwritingAction.OPEN_EMOJI,
            KeyEvent.KEYCODE_3 to HandwritingAction.DELETE_TEXT,
            KeyEvent.KEYCODE_4 to HandwritingAction.OPEN_NUMBER,
            KeyEvent.KEYCODE_6 to HandwritingAction.INSERT_SPACE,
            KeyEvent.KEYCODE_7 to HandwritingAction.SWITCH_LANGUAGE,
            KeyEvent.KEYCODE_9 to HandwritingAction.INSERT_COMMA
        ).forEach { (keyCode, expectedAction) ->
            fixture.keyDown(keyCode)
            fixture.keyUp(keyCode)
            assertEquals(expectedAction, fixture.actions.last())
        }

        fixture.keyDown(KeyEvent.KEYCODE_STAR)
        assertEquals(HandwritingAction.OPEN_SYMBOLS, fixture.actions.last())
    }

    @Test
    fun longNumberCommitsShortcutOnlyOnceAndShortNumberDoesNothing() {
        val fixture = Fixture(hasCandidates = true)

        fixture.keyDown(KeyEvent.KEYCODE_2)
        fixture.keyUp(KeyEvent.KEYCODE_2)
        assertEquals(emptyList<Int>(), fixture.shortcuts)
        assertEquals(emptyList<HandwritingAction>(), fixture.actions)

        fixture.keyDown(KeyEvent.KEYCODE_2)
        fixture.keyDown(KeyEvent.KEYCODE_2, repeatCount = 1, eventTime = 600L)
        fixture.keyDown(KeyEvent.KEYCODE_2, repeatCount = 2, eventTime = 800L)
        fixture.keyUp(KeyEvent.KEYCODE_2)

        assertEquals(listOf(1), fixture.shortcuts)
    }

    @Test
    fun longRailShortcutSelectsCandidateWithoutPerformingItsShortAction() {
        val fixture = Fixture(hasCandidates = true)

        fixture.keyDown(KeyEvent.KEYCODE_3)
        fixture.keyDown(KeyEvent.KEYCODE_3, repeatCount = 1, eventTime = 600L)
        fixture.keyUp(KeyEvent.KEYCODE_3)

        assertEquals(listOf(2), fixture.shortcuts)
        assertEquals(emptyList<HandwritingAction>(), fixture.actions)
    }

    @Test
    fun shortZeroConfirmsHighlightedCandidateWhileLongZeroSelectsShortcutTen() {
        val fixture = Fixture(hasCandidates = true)

        fixture.keyDown(KeyEvent.KEYCODE_0)
        fixture.keyUp(KeyEvent.KEYCODE_0)
        assertEquals(1, fixture.commitCurrentCount)

        fixture.keyDown(KeyEvent.KEYCODE_0)
        fixture.keyDown(KeyEvent.KEYCODE_0, repeatCount = 1, eventTime = 600L)
        fixture.keyUp(KeyEvent.KEYCODE_0)

        assertEquals(1, fixture.commitCurrentCount)
        assertEquals(listOf(9), fixture.shortcuts)
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
        var clearCount = 0
        var commitCurrentCount = 0
        val shortcuts = mutableListOf<Int>()
        val moves = mutableListOf<Int>()
        val pages = mutableListOf<Int>()
        val actions = mutableListOf<HandwritingAction>()

        val handler = PhysicalHandwritingKeyHandler(
            longPressDelayMillis = { 500 },
            hasStrokes = { hasStrokes },
            hasCandidates = { hasCandidates },
            clearPendingCharacter = {
                (hasStrokes || hasCandidates).also { cleared ->
                    if (cleared) {
                        clearCount++
                        hasStrokes = false
                        hasCandidates = false
                    }
                }
            },
            moveCandidate = { moves += it; true },
            offsetPage = { pages += it; true },
            commitCurrentCandidate = { commitCurrentCount++; true },
            commitShortcut = { shortcuts += it; true },
            performAction = actions::add
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

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalT9KeyHandlerTest {

    @Test
    fun smartEnglishShortPressAppendsDigitOnKeyUpOnly() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true
        )
        val handler = PhysicalT9KeyHandler(host)

        val down = handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN))
        assertTrue(down.handled)
        assertEquals(emptyList<Int>(), host.appendedSmartEnglishDigits)
        assertEquals(emptyList<String>(), host.committedTexts)

        val up = handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP))
        assertTrue(up.handled)
        assertEquals(listOf(2), host.appendedSmartEnglishDigits)
        assertEquals(emptyList<String>(), host.committedTexts)
    }

    @Test
    fun smartEnglishLongPressCommitsLiteralDigitAndConsumesKeyUp() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true
        )
        val handler = PhysicalT9KeyHandler(host)

        handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN))
        val repeat = handler.handleKeyDown(
            keyInput(
                keyCode = KeyEvent.KEYCODE_2,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                eventTime = 700L
            )
        )
        assertTrue(repeat.handled)
        assertEquals(listOf("2"), host.committedTexts)
        assertEquals(1, host.resetSmartEnglishCount)
        assertEquals(1, host.flushEnglishLearningCount)

        val up = handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP))
        assertTrue(up.handled)
        assertEquals(emptyList<Int>(), host.appendedSmartEnglishDigits)
        assertEquals(listOf("2"), host.committedTexts)
    }

    @Test
    fun smartEnglishNavigationConsumesKeyUp() {
        val host = FakeHost(
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true,
            hasSmartEnglishDigits = true
        )
        val handler = PhysicalT9KeyHandler(host)

        val result = handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN))

        assertTrue(result.handled)
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, result.consumedKeyUp)
        assertEquals(listOf(-1), host.hanziPageOffsets)
    }

    @Test
    fun ignoresKeysOutsideInputMode() {
        val host = FakeHost(
            isInInputMode = false,
            mode = PhysicalT9KeyHandler.Mode.ENGLISH,
            isSmartEnglishActive = true
        )
        val handler = PhysicalT9KeyHandler(host)

        assertFalse(handler.handleKeyDown(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN)).handled)
        assertFalse(handler.handleKeyUp(keyInput(KeyEvent.KEYCODE_2, KeyEvent.ACTION_UP)).handled)
    }

    private fun keyInput(
        keyCode: Int,
        action: Int,
        repeatCount: Int = 0,
        downTime: Long = 0L,
        eventTime: Long = downTime
    ): PhysicalT9KeyHandler.KeyInput = PhysicalT9KeyHandler.KeyInput(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        downTime = downTime,
        eventTime = eventTime
    )

    private class FakeHost(
        override val isInInputMode: Boolean = true,
        override var mode: PhysicalT9KeyHandler.Mode = PhysicalT9KeyHandler.Mode.CHINESE,
        override var isSmartEnglishActive: Boolean = false,
        override var chineseComposing: Boolean = false,
        override var compositionKeyCount: Int = 0,
        override var hasPendingPunctuation: Boolean = false,
        private var pendingPunctuationOneKeyDeferredState: Boolean = false,
        override var pendingPunctuationSet: PhysicalT9KeyHandler.PunctuationSet =
            PhysicalT9KeyHandler.PunctuationSet.CHINESE,
        override var hasSmartEnglishDigits: Boolean = false,
        override var hasMultiTapPendingChar: Boolean = false,
        override var hasTopPinyinCandidates: Boolean = false,
        override var candidateFocus: PhysicalT9KeyHandler.CandidateFocus =
            PhysicalT9KeyHandler.CandidateFocus.BOTTOM
    ) : PhysicalT9KeyHandler.Host {
        val committedTexts = mutableListOf<String>()
        val appendedSmartEnglishDigits = mutableListOf<Int>()
        val hanziPageOffsets = mutableListOf<Int>()
        var resetSmartEnglishCount = 0
        var flushEnglishLearningCount = 0
        override val pendingPunctuationOneKeyDeferred: Boolean
            get() = pendingPunctuationOneKeyDeferredState

        override fun keyHeldPastLongPressDelay(input: PhysicalT9KeyHandler.KeyInput): Boolean =
            input.repeatCount > 0 && input.eventTime - input.downTime >= 500L

        override fun setPendingPunctuationOneKeyDeferred(value: Boolean) {
            pendingPunctuationOneKeyDeferredState = value
        }

        override fun commitPendingPunctuationShortcut(keyCode: Int): Boolean = false
        override fun commitHanziShortcut(keyCode: Int): Boolean = false
        override fun commitSmartEnglishShortcut(keyCode: Int): Boolean = false
        override fun commitPendingPunctuation(): Boolean = false
        override fun cancelPendingPunctuation(): Boolean = false
        override fun handleChinesePunctuationKey(): Boolean = false
        override fun togglePendingPunctuationSet(): Boolean = false
        override fun switchToNextMode() = Unit

        override fun commitText(text: String) {
            committedTexts += text
        }

        override fun commitNumberOperatorForKey(keyCode: Int, fallbackDigit: Int): Boolean = false
        override fun showNumberOperatorHintPanel() = Unit
        override fun commitLiteralStarInCurrentChineseState() = Unit
        override fun handleEnglishStarShortPress() = Unit
        override fun handleEnglishStarLongPress() = Unit
        override fun handleMultiTapKey(keyCode: Int): Boolean = false
        override fun commitMultiTapChar(): Boolean = false
        override fun cancelMultiTapChar() = Unit
        override fun deferSmartEnglishPunctuationKey() = Unit
        override fun showSmartEnglishPunctuationCandidates() = Unit

        override fun appendSmartEnglishDigit(digit: Int) {
            appendedSmartEnglishDigits += digit
            hasSmartEnglishDigits = true
        }

        override fun resetSmartEnglishT9() {
            resetSmartEnglishCount += 1
            hasSmartEnglishDigits = false
        }

        override fun commitSmartEnglishCandidate(): Boolean = false
        override fun moveSmartEnglishCandidate(delta: Int): Boolean = false
        override fun smartEnglishBackspace(): Boolean = false

        override fun flushEnglishLearningWord() {
            flushEnglishLearningCount += 1
        }

        override fun handleReturnKey() = Unit
        override fun forwardChineseT9KeyShortPress(
            keyCode: Int,
            input: PhysicalT9KeyHandler.KeyInput
        ): Boolean = false

        override fun forwardChineseT9SeparatorShortPress(): Boolean = false
        override fun moveCandidateFocus(focus: PhysicalT9KeyHandler.CandidateFocus) {
            candidateFocus = focus
        }

        override fun moveHighlightedPinyin(delta: Int): Boolean = false
        override fun moveHighlightedHanzi(delta: Int): Boolean = false

        override fun offsetHanziPage(delta: Int): Boolean {
            hanziPageOffsets += delta
            return false
        }

        override fun commitHighlightedPinyin(): Boolean = false
        override fun commitHighlightedHanzi(): Boolean = false
    }
}

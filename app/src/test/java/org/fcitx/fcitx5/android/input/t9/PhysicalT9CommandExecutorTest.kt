/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalT9CommandExecutorTest {

    @Test
    fun commitEnglishPendingOrReturnKeepsFallbackOrderLocalToExecutor() {
        val host = RecordingHost(
            commitSmartEnglishCandidateResult = false,
            commitMultiTapCharResult = false,
            commitPendingPunctuationResult = false
        )
        val executor = PhysicalT9CommandExecutor(host)

        executor.execute(
            listOf(PhysicalT9KeyFlow.Command.CommitEnglishPendingOrReturn),
            input()
        )

        assertEquals(
            listOf("commitSmartEnglishCandidate:true:true", "commitMultiTapChar", "commitPendingPunctuation", "return"),
            host.calls
        )
    }

    @Test
    fun commitEnglishPendingOrReturnStopsAfterFirstCommittedPendingInput() {
        val host = RecordingHost(commitSmartEnglishCandidateResult = true)
        val executor = PhysicalT9CommandExecutor(host)

        executor.execute(
            listOf(PhysicalT9KeyFlow.Command.CommitEnglishPendingOrReturn),
            input()
        )

        assertEquals(listOf("commitSmartEnglishCandidate:true:true"), host.calls)
    }

    @Test
    fun commitEnglishPendingOrSpaceDoesNotAddLiteralSpaceAfterSmartEnglishCommit() {
        val host = RecordingHost(commitSmartEnglishCandidateResult = true)
        val executor = PhysicalT9CommandExecutor(host)

        executor.execute(
            listOf(PhysicalT9KeyFlow.Command.CommitEnglishPendingOrSpace),
            input()
        )

        assertEquals(listOf("commitSmartEnglishCandidate:true:true"), host.calls)
    }

    @Test
    fun commitEnglishPendingOrSpaceAddsLiteralSpaceWhenNoSmartEnglishCandidateCommits() {
        val host = RecordingHost(
            commitSmartEnglishCandidateResult = false,
            commitMultiTapCharResult = true,
            commitPendingPunctuationResult = false
        )
        val executor = PhysicalT9CommandExecutor(host)

        executor.execute(
            listOf(PhysicalT9KeyFlow.Command.CommitEnglishPendingOrSpace),
            input()
        )

        assertEquals(
            listOf(
                "commitSmartEnglishCandidate:true:true",
                "commitMultiTapChar",
                "commitPendingPunctuation",
                "commitText: "
            ),
            host.calls
        )
    }

    @Test
    fun bottomCandidateFallbackRunsOnlyWhenVisibleRowDoesNotCommit() {
        val host = RecordingHost(commitHighlightedBottomCandidateResult = false)
        val executor = PhysicalT9CommandExecutor(host)

        executor.execute(
            listOf(
                PhysicalT9KeyFlow.Command.CommitBottomCandidate(
                    PhysicalT9KeyFlow.BottomCandidateFallback.SMART_ENGLISH
                )
            ),
            input()
        )

        assertEquals(
            listOf("commitHighlightedBottomCandidate", "commitSmartEnglishCandidate:true:true"),
            host.calls
        )
    }

    @Test
    fun shortcutTextFallbackCancelsPendingPunctuationBeforeLiteralText() {
        val host = RecordingHost(commitPendingPunctuationShortcutResult = false)
        val executor = PhysicalT9CommandExecutor(host)

        executor.execute(
            listOf(PhysicalT9KeyFlow.Command.CommitPendingPunctuationShortcutOrText(KeyEvent.KEYCODE_9, "9")),
            input()
        )

        assertEquals(
            listOf("commitPendingPunctuationShortcut:16", "cancelPendingPunctuation", "commitText:9"),
            host.calls
        )
    }

    private fun input(): PhysicalT9KeyHandler.KeyInput =
        PhysicalT9KeyHandler.KeyInput(
            keyCode = KeyEvent.KEYCODE_0,
            action = KeyEvent.ACTION_UP,
            repeatCount = 0,
            downTime = 0L,
            eventTime = 0L
        )

    private class RecordingHost(
        override val isInInputMode: Boolean = true,
        override val mode: PhysicalT9KeyHandler.Mode = PhysicalT9KeyHandler.Mode.ENGLISH,
        override val isSmartEnglishActive: Boolean = true,
        override val chineseComposing: Boolean = false,
        override val compositionKeyCount: Int = 0,
        override val hasPendingPunctuation: Boolean = false,
        override val hasSmartEnglishDigits: Boolean = false,
        override val hasSmartEnglishCandidates: Boolean = false,
        override val hasMultiTapPendingChar: Boolean = false,
        override val hasTopPinyinCandidates: Boolean = false,
        override val hasBottomCandidateRow: Boolean = false,
        override val candidateFocus: PhysicalT9KeyHandler.CandidateFocus =
            PhysicalT9KeyHandler.CandidateFocus.BOTTOM,
        private val commitPendingPunctuationShortcutResult: Boolean = false,
        private val commitPendingPunctuationResult: Boolean = false,
        private val commitSmartEnglishCandidateResult: Boolean = false,
        private val commitMultiTapCharResult: Boolean = false,
        private val commitHighlightedBottomCandidateResult: Boolean = false
    ) : PhysicalT9KeyHandler.Host {
        val calls = mutableListOf<String>()

        override fun keyHeldPastLongPressDelay(input: PhysicalT9KeyHandler.KeyInput): Boolean = false
        override fun commitPendingPunctuationShortcut(keyCode: Int): Boolean {
            calls += "commitPendingPunctuationShortcut:$keyCode"
            return commitPendingPunctuationShortcutResult
        }
        override fun commitHanziShortcut(keyCode: Int): Boolean {
            calls += "commitHanziShortcut:$keyCode"
            return false
        }
        override fun commitSmartEnglishShortcut(keyCode: Int): Boolean {
            calls += "commitSmartEnglishShortcut:$keyCode"
            return false
        }
        override fun commitPendingPunctuation(): Boolean {
            calls += "commitPendingPunctuation"
            return commitPendingPunctuationResult
        }
        override fun cancelPendingPunctuation(): Boolean {
            calls += "cancelPendingPunctuation"
            return false
        }
        override fun showChinesePunctuationCandidates() {
            calls += "showChinesePunctuationCandidates"
        }
        override fun showEnglishPunctuationCandidates() {
            calls += "showEnglishPunctuationCandidates"
        }
        override fun commitChineseCandidateAndShowPunctuation() {
            calls += "commitChineseCandidateAndShowPunctuation"
        }
        override fun togglePendingPunctuationSet(): Boolean {
            calls += "togglePendingPunctuationSet"
            return false
        }
        override fun switchToNextMode() {
            calls += "switchToNextMode"
        }
        override fun commitText(text: String) {
            calls += "commitText:$text"
        }
        override fun commitNumberOperatorForKey(keyCode: Int, fallbackDigit: Int): Boolean {
            calls += "commitNumberOperatorForKey:$keyCode:$fallbackDigit"
            return false
        }
        override fun showNumberOperatorHintPanel() {
            calls += "showNumberOperatorHintPanel"
        }
        override fun commitLiteralStar() {
            calls += "commitLiteralStar"
        }
        override fun cycleEnglishCase() {
            calls += "cycleEnglishCase"
        }
        override fun handleMultiTapKey(keyCode: Int): Boolean {
            calls += "handleMultiTapKey:$keyCode"
            return false
        }
        override fun commitMultiTapChar(): Boolean {
            calls += "commitMultiTapChar"
            return commitMultiTapCharResult
        }
        override fun cancelMultiTapChar() {
            calls += "cancelMultiTapChar"
        }
        override fun appendSmartEnglishDigit(digit: Int) {
            calls += "appendSmartEnglishDigit:$digit"
        }
        override fun resetSmartEnglishT9() {
            calls += "resetSmartEnglishT9"
        }
        override fun commitSmartEnglishCandidate(
            appendSpace: Boolean,
            continuePrediction: Boolean
        ): Boolean {
            calls += "commitSmartEnglishCandidate:$appendSpace:$continuePrediction"
            return commitSmartEnglishCandidateResult
        }
        override fun moveSmartEnglishCandidate(delta: Int): Boolean {
            calls += "moveSmartEnglishCandidate:$delta"
            return false
        }
        override fun smartEnglishBackspace(): Boolean {
            calls += "smartEnglishBackspace"
            return false
        }
        override fun flushEnglishLearningWord() {
            calls += "flushEnglishLearningWord"
        }
        override fun handleReturnKey() {
            calls += "return"
        }
        override fun forwardChineseT9KeyShortPress(
            keyCode: Int,
            input: PhysicalT9KeyHandler.KeyInput
        ): Boolean {
            calls += "forwardChineseT9KeyShortPress:$keyCode"
            return false
        }
        override fun forwardChineseT9SeparatorShortPress(): Boolean {
            calls += "forwardChineseT9SeparatorShortPress"
            return false
        }
        override fun discardChineseCompositionForModeSwitch() {
            calls += "discardChineseCompositionForModeSwitch"
        }
        override fun moveCandidateFocus(focus: PhysicalT9KeyHandler.CandidateFocus) {
            calls += "moveCandidateFocus:$focus"
        }
        override fun moveHighlightedPinyin(delta: Int): Boolean {
            calls += "moveHighlightedPinyin:$delta"
            return false
        }
        override fun moveHighlightedBottomCandidate(delta: Int): Boolean {
            calls += "moveHighlightedBottomCandidate:$delta"
            return false
        }
        override fun offsetBottomCandidatePage(delta: Int): Boolean {
            calls += "offsetBottomCandidatePage:$delta"
            return false
        }
        override fun commitHighlightedPinyin(): Boolean {
            calls += "commitHighlightedPinyin"
            return false
        }
        override fun commitHighlightedBottomCandidate(): Boolean {
            calls += "commitHighlightedBottomCandidate"
            return commitHighlightedBottomCandidateResult
        }
    }
}

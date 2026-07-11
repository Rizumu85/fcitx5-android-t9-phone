/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalT9KeyHostAdapterTest {

    @Test
    fun mapsDomainModeAndCandidateFocusToHandlerTypes() {
        var mode = T9InputMode.CHINESE
        var focus = T9CandidateFocus.TOP
        val host = host(
            mode = { mode },
            candidateFocus = { focus }
        )

        assertEquals(PhysicalT9KeyHandler.Mode.CHINESE, host.mode)
        assertEquals(PhysicalT9KeyHandler.CandidateFocus.TOP, host.candidateFocus)

        mode = T9InputMode.ENGLISH
        focus = T9CandidateFocus.BOTTOM

        assertEquals(PhysicalT9KeyHandler.Mode.ENGLISH, host.mode)
        assertEquals(PhysicalT9KeyHandler.CandidateFocus.BOTTOM, host.candidateFocus)
    }

    @Test
    fun delegatesHostActionsToGroupedAdapters() {
        val calls = mutableListOf<String>()
        val host = host(
            commitShortcut = {
                calls += "punctuation:$it"
                true
            },
            appendSmartEnglishDigit = {
                calls += "english:$it"
            },
            moveFocus = {
                calls += "focus:$it"
            },
            commitText = {
                calls += "text:$it"
            }
        )

        assertTrue(host.commitPendingPunctuationShortcut(7))
        host.appendSmartEnglishDigit(2)
        host.moveCandidateFocus(PhysicalT9KeyHandler.CandidateFocus.BOTTOM)
        host.commitText("x")

        assertEquals(
            listOf(
                "punctuation:7",
                "english:2",
                "focus:BOTTOM",
                "text:x"
            ),
            calls
        )
    }

    private fun host(
        mode: () -> T9InputMode = { T9InputMode.CHINESE },
        candidateFocus: () -> T9CandidateFocus = { T9CandidateFocus.BOTTOM },
        commitShortcut: (Int) -> Boolean = { false },
        appendSmartEnglishDigit: (Int) -> Unit = {},
        moveFocus: (T9CandidateFocus) -> Unit = {},
        commitText: (String) -> Unit = {},
        switchToVoiceInput: () -> Unit = {},
        discardChineseCompositionForModeSwitch: () -> Unit = {}
    ): PhysicalT9KeyHostAdapter =
        PhysicalT9KeyHostAdapter(
            state = PhysicalT9KeyHostAdapter.State(
                isInInputMode = { true },
                mode = mode,
                isSmartEnglishActive = { false },
                chineseComposing = { false },
                compositionKeyCount = { 0 },
                hasPendingPunctuation = { false },
                hasSmartEnglishDigits = { false },
                hasSmartEnglishCandidates = { false },
                hasMultiTapPendingChar = { false },
                hasTopReadingCandidates = { false },
                hasBottomCandidateRow = { false },
                candidateFocus = candidateFocus,
                keyHeldPastLongPressDelay = { false }
            ),
            punctuation = PhysicalT9KeyHostAdapter.PunctuationActions(
                commitShortcut = commitShortcut,
                commit = { false },
                cancel = { false },
                showChineseCandidates = {},
                showEnglishCandidates = {},
                toggleSet = { false }
            ),
            english = PhysicalT9KeyHostAdapter.EnglishActions(
                commitSmartEnglishShortcut = { false },
                appendSmartEnglishDigit = appendSmartEnglishDigit,
                resetSmartEnglish = {},
                commitSmartEnglishCandidate = { _, _ -> false },
                moveSmartEnglishCandidate = { false },
                smartEnglishBackspace = { false },
                flushLearningWord = {},
                cycleCase = {},
                handleMultiTapKey = { false },
                commitMultiTapChar = { false },
                cancelMultiTapChar = {}
            ),
            candidates = PhysicalT9KeyHostAdapter.CandidateActions(
                commitHanziShortcut = { false },
                moveFocus = moveFocus,
                moveHighlightedReading = { false },
                moveHighlightedBottomCandidate = { false },
                offsetBottomCandidatePage = { false },
                commitHighlightedReading = { false },
                commitHighlightedBottomCandidate = { false },
                commitChineseCandidateAndShowPunctuation = {}
            ),
            platform = PhysicalT9KeyHostAdapter.PlatformActions(
                switchToNextMode = {},
                switchToVoiceInput = switchToVoiceInput,
                commitText = commitText,
                commitNumberOperatorForKey = { _, _ -> false },
                showNumberOperatorHintPanel = {},
                commitLiteralStar = {},
                handleReturnKey = {},
                commitChineseCodePreview = { false },
                cycleChineseScheme = { false },
                forwardChineseT9KeyShortPress = { _, _ -> false },
                forwardChineseT9SeparatorShortPress = { false },
                discardChineseCompositionForModeSwitch = discardChineseCompositionForModeSwitch
            )
        )
}

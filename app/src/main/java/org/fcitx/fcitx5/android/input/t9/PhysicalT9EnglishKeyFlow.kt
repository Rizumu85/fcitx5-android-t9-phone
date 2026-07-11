/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyFlow.Command
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyFlow.Decision
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyFlow.State

internal class PhysicalT9EnglishKeyFlow(
    private val session: PhysicalT9KeyFlowSession
) {
    fun handle(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.keyCode) {
        KeyEvent.KEYCODE_1 -> handleEnglishOne(input, state)
        KeyEvent.KEYCODE_POUND -> handleEnglishPound(input, state)
        KeyEvent.KEYCODE_0 -> handleEnglishZero(input, state)
        KeyEvent.KEYCODE_STAR -> handleEnglishStar(input, state)
        else -> {
            val digit = PhysicalT9KeyPolicy.t9Digit(input.keyCode)
            when {
                digit != null && digit in 2..9 ->
                    handleEnglishPredictiveDigit(input, state, digit)
                isEnglishNavigationOrDeleteKey(input.keyCode) ->
                    handleEnglishNavigationOrDelete(input, state)
                else -> null
            }
        }
    }

    private fun isEnglishNavigationOrDeleteKey(keyCode: Int): Boolean =
        PhysicalT9KeyPolicy.focusKey(keyCode) != null ||
            keyCode == KeyEvent.KEYCODE_SPACE ||
            PhysicalT9KeyPolicy.isDeleteKey(keyCode)

    private fun isDigitLongPressFlagSet(keyCode: Int): Boolean =
        session.isDigitLongPressFlagSet(keyCode)

    private fun setDigitLongPressFlag(keyCode: Int, value: Boolean) {
        session.setDigitLongPressFlag(keyCode, value)
    }

    private fun handleEnglishOne(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(input.keyCode, false)
                Decision(handled = true)
            } else if (!isDigitLongPressFlagSet(input.keyCode) && state.heldPastLongPressDelay) {
                setDigitLongPressFlag(input.keyCode, true)
                Decision(
                    handled = true,
                    commands = when {
                        state.hasPendingPunctuation -> listOf(
                            Command.CommitPendingPunctuationShortcut(KeyEvent.KEYCODE_1)
                        )
                        state.isSmartEnglishActive && state.hasSmartEnglishCandidates -> listOf(
                            Command.CommitSmartEnglishShortcut(KeyEvent.KEYCODE_1)
                        )
                        else -> listOf(
                            Command.CancelMultiTapChar,
                            Command.CommitText("1"),
                            Command.FlushEnglishLearningWord
                        )
                    }
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            val wasLongPress = isDigitLongPressFlagSet(input.keyCode)
            setDigitLongPressFlag(input.keyCode, false)
            Decision(
                handled = true,
                commands = when {
                    wasLongPress || state.hasPendingPunctuation -> emptyList()
                    else -> listOf(Command.CycleEnglishCase)
                }
            )
        }
        else -> null
    }

    private fun handleEnglishZero(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? =
        if (state.isSmartEnglishActive) {
            handleSmartEnglishZero(input, state)
        } else {
            handleSimpleEnglishZero(input, state)
        }

    private fun handleEnglishPredictiveDigit(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State,
        digit: Int
    ): Decision? =
        if (state.isSmartEnglishActive) {
            handleSmartEnglishPredictiveDigit(input, state, digit)
        } else {
            handleSimpleEnglishPredictiveDigit(input, state, digit)
        }

    private fun handleSmartEnglishZero(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(input.keyCode, false)
                Decision(handled = true)
            } else if (!isDigitLongPressFlagSet(input.keyCode) && state.heldPastLongPressDelay) {
                setDigitLongPressFlag(input.keyCode, true)
                Decision(
                    handled = true,
                    commands = when {
                        state.hasPendingPunctuation -> listOf(
                            Command.CommitPendingPunctuationShortcutOrText(
                                keyCode = input.keyCode,
                                text = "0"
                            )
                        )
                        state.hasSmartEnglishCandidates -> listOf(
                            Command.CommitSmartEnglishShortcut(input.keyCode)
                        )
                        state.idleLongZeroVoiceEnabled &&
                            !state.hasSmartEnglishDigits &&
                            !state.hasMultiTapPendingChar &&
                            !state.hasBottomCandidateRow -> listOf(Command.SwitchToVoiceInput)
                        else -> listOf(
                            Command.CancelMultiTapChar,
                            Command.CommitText("0")
                        )
                    }
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            val wasLongPress = isDigitLongPressFlagSet(input.keyCode)
            setDigitLongPressFlag(input.keyCode, false)
            Decision(
                handled = true,
                commands = if (wasLongPress) {
                    emptyList()
                } else {
                    listOf(
                        Command.CommitEnglishPendingOrSpace,
                        Command.FlushEnglishLearningWord
                    )
                }
            )
        }
        else -> null
    }

    private fun handleSimpleEnglishZero(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(input.keyCode, false)
                Decision(handled = true)
            } else if (!isDigitLongPressFlagSet(input.keyCode) && state.heldPastLongPressDelay) {
                setDigitLongPressFlag(input.keyCode, true)
                Decision(
                    handled = true,
                    commands = if (
                        state.idleLongZeroVoiceEnabled && !state.hasMultiTapPendingChar
                    ) {
                        listOf(Command.SwitchToVoiceInput)
                    } else {
                        listOf(
                            Command.CancelMultiTapChar,
                            Command.CommitText("0")
                        )
                    }
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            val wasLongPress = isDigitLongPressFlagSet(input.keyCode)
            setDigitLongPressFlag(input.keyCode, false)
            Decision(
                handled = true,
                commands = if (wasLongPress) {
                    emptyList()
                } else {
                    listOf(
                        Command.CommitEnglishPendingOrSpace,
                        Command.FlushEnglishLearningWord
                    )
                }
            )
        }
        else -> null
    }

    private fun handleSmartEnglishPredictiveDigit(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State,
        digit: Int
    ): Decision? {
        return if (state.hasPendingPunctuation) {
            handleSmartEnglishPendingPunctuationDigit(input, state, digit)
        } else {
            handleSmartEnglishInputDigit(input, state, digit)
        }
    }

    private fun handleSmartEnglishPendingPunctuationDigit(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State,
        digit: Int
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(input.keyCode, false)
                Decision(handled = true)
            } else if (!isDigitLongPressFlagSet(input.keyCode) && state.heldPastLongPressDelay) {
                setDigitLongPressFlag(input.keyCode, true)
                Decision(
                    handled = true,
                    commands = listOf(Command.CommitPendingPunctuationShortcut(input.keyCode))
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            val wasLongPress = isDigitLongPressFlagSet(input.keyCode)
            setDigitLongPressFlag(input.keyCode, false)
            Decision(
                handled = true,
                commands = if (wasLongPress) {
                    emptyList()
                } else {
                    listOf(
                        Command.CommitPendingPunctuation,
                        Command.AppendSmartEnglishDigit(digit)
                    )
                }
            )
        }
        else -> null
    }

    private fun handleSmartEnglishInputDigit(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State,
        digit: Int
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(input.keyCode, false)
                session.pendingSmartEnglishDigitKeyCode = input.keyCode
                session.pendingSmartEnglishDigit = digit
                Decision(handled = true)
            } else if (!isDigitLongPressFlagSet(input.keyCode) && state.heldPastLongPressDelay) {
                setDigitLongPressFlag(input.keyCode, true)
                resetSmartEnglishPendingDigit()
                Decision(
                    handled = true,
                    commands = if (state.hasSmartEnglishCandidates) {
                        listOf(Command.CommitSmartEnglishShortcut(input.keyCode))
                    } else {
                        listOf(
                            Command.ResetSmartEnglishT9,
                            Command.CommitText(digit.toString()),
                            Command.FlushEnglishLearningWord
                        )
                    }
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            if (session.pendingSmartEnglishDigitKeyCode != input.keyCode) {
                return Decision(handled = true)
            }
            val pendingDigit = session.pendingSmartEnglishDigit
            resetSmartEnglishPendingDigit()
            val wasLongPress = isDigitLongPressFlagSet(input.keyCode)
            setDigitLongPressFlag(input.keyCode, false)
            Decision(
                handled = true,
                commands = if (wasLongPress) {
                    emptyList()
                } else {
                    listOf(Command.AppendSmartEnglishDigit(pendingDigit))
                }
            )
        }
        else -> null
    }

    private fun handleSimpleEnglishPredictiveDigit(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State,
        digit: Int
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(input.keyCode, false)
                Decision(
                    handled = true,
                    commands = listOf(Command.HandleMultiTapKey(input.keyCode))
                )
            } else if (!isDigitLongPressFlagSet(input.keyCode) && state.heldPastLongPressDelay) {
                setDigitLongPressFlag(input.keyCode, true)
                Decision(
                    handled = true,
                    commands = listOf(
                        Command.CancelMultiTapChar,
                        Command.CommitText(digit.toString())
                    )
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            setDigitLongPressFlag(input.keyCode, false)
            Decision(handled = true)
        }
        else -> null
    }

    private fun handleEnglishNavigationOrDelete(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? {
        val focusKey = PhysicalT9KeyPolicy.focusKey(input.keyCode)
        if (!state.isSmartEnglishActive && focusKey == PhysicalT9KeyPolicy.FocusKey.OK) {
            return handleEnglishOk(input, state)
        }
        if (input.action != KeyEvent.ACTION_DOWN) return null
        if (PhysicalT9KeyPolicy.isDeleteKey(input.keyCode)) {
            return when {
                state.hasSmartEnglishCandidates || state.hasPendingPunctuation ->
                    Decision(
                        handled = true,
                        commands = listOf(Command.SmartEnglishDelete(state.hasPendingPunctuation)),
                        consumedKeyUp = input.keyCode
                    )
                state.hasMultiTapPendingChar ->
                    Decision(
                        handled = true,
                        commands = listOf(Command.CancelMultiTapChar)
                    )
                else -> null
            }
        }
        if (!state.isSmartEnglishActive ||
            (!state.hasSmartEnglishCandidates && !state.hasPendingPunctuation)
        ) {
            return null
        }
        return PhysicalT9SelectionMode.handle(
            input = input,
            state = state,
            surface = PhysicalT9SelectionMode.Surface.SMART_ENGLISH
        )
    }

    private fun handleSmartEnglishPound(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                session.poundLongPressTriggered = false
                Decision(handled = true)
            } else if (!session.poundLongPressTriggered && state.heldPastLongPressDelay) {
                session.poundLongPressTriggered = true
                Decision(
                    handled = true,
                    commands = buildList {
                        if (state.hasPendingPunctuation) add(Command.CommitPendingPunctuation)
                        add(Command.SwitchToNextMode)
                    }
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            if (session.poundLongPressTriggered) {
                session.poundLongPressTriggered = false
                Decision(handled = true)
            } else {
                Decision(
                    handled = true,
                    commands = when {
                        state.hasPendingPunctuation -> listOf(Command.CommitPendingPunctuation)
                        state.hasSmartEnglishCandidates -> listOf(
                            Command.CommitSmartEnglishCandidate(
                                appendSpace = false,
                                continuePrediction = false
                            ),
                            Command.HandleReturnKey
                        )
                        else -> listOf(Command.HandleReturnKey)
                    }
                )
            }
        }
        else -> null
    }

    private fun handleEnglishPound(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? =
        if (state.isSmartEnglishActive) {
            handleSmartEnglishPound(input, state)
        } else {
            handleSimpleEnglishPound(input, state)
        }

    private fun handleSimpleEnglishPound(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                session.poundLongPressTriggered = false
                Decision(handled = true)
            } else if (!session.poundLongPressTriggered && state.heldPastLongPressDelay) {
                session.poundLongPressTriggered = true
                Decision(handled = true, commands = listOf(Command.SwitchToNextMode))
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            if (session.poundLongPressTriggered) {
                session.poundLongPressTriggered = false
                Decision(handled = true)
            } else {
                Decision(
                    handled = true,
                    commands = if (state.hasPendingPunctuation) {
                        listOf(Command.CommitPendingPunctuation)
                    } else {
                        listOf(Command.CommitEnglishPendingOrReturn)
                    }
                )
            }
        }
        else -> null
    }

    private fun handleEnglishStar(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(input.keyCode, false)
                Decision(handled = true)
            } else if (!isDigitLongPressFlagSet(input.keyCode) && state.heldPastLongPressDelay) {
                setDigitLongPressFlag(input.keyCode, true)
                Decision(
                    handled = true,
                    commands = buildList {
                        when {
                            state.hasPendingPunctuation -> add(Command.CancelPendingPunctuation)
                            state.isSmartEnglishActive && state.hasSmartEnglishCandidates -> add(
                                Command.CommitSmartEnglishCandidate(
                                    appendSpace = false,
                                    continuePrediction = false
                                )
                            )
                            state.hasMultiTapPendingChar -> add(Command.CommitMultiTapChar)
                        }
                        add(Command.CommitLiteralStar)
                        add(Command.FlushEnglishLearningWord)
                    }
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            val wasLongPress = isDigitLongPressFlagSet(input.keyCode)
            setDigitLongPressFlag(input.keyCode, false)
            Decision(
                handled = true,
                commands = when {
                    wasLongPress -> emptyList()
                    state.hasPendingPunctuation -> listOf(Command.TogglePendingPunctuationSet)
                    state.isSmartEnglishActive && state.hasSmartEnglishCandidates -> listOf(
                        Command.CommitSmartEnglishCandidate(
                            appendSpace = false,
                            continuePrediction = false
                        ),
                        Command.ShowEnglishPunctuationCandidates
                    )
                    state.hasMultiTapPendingChar -> listOf(
                        Command.CommitMultiTapChar,
                        Command.ShowEnglishPunctuationCandidates
                    )
                    else -> listOf(Command.ShowEnglishPunctuationCandidates)
                }
            )
        }
        else -> null
    }

    private fun handleEnglishOk(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (!state.hasMultiTapPendingChar) return null
            Decision(
                handled = true,
                commands = if (input.repeatCount == 0) {
                    listOf(Command.CommitMultiTapChar)
                } else {
                    emptyList()
                }
            )
        }
        KeyEvent.ACTION_UP -> Decision(handled = true)
        else -> null
    }

    private fun resetSmartEnglishPendingDigit() {
        session.resetSmartEnglishPendingDigit()
    }
}

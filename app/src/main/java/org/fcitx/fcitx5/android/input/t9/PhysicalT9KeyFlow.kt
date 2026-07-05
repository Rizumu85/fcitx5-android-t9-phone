/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

class PhysicalT9KeyFlow {

    data class State(
        val mode: PhysicalT9KeyHandler.Mode,
        val isSmartEnglishActive: Boolean,
        val hasPendingPunctuation: Boolean,
        val pendingPunctuationOneKeyDeferred: Boolean,
        val pendingPunctuationSet: PhysicalT9KeyHandler.PunctuationSet,
        val hasSmartEnglishCandidates: Boolean,
        val heldPastLongPressDelay: Boolean
    )

    data class Decision(
        val handled: Boolean,
        val commands: List<Command> = emptyList(),
        val consumedKeyUp: Int? = null
    )

    sealed class Command {
        data class SetPendingPunctuationOneKeyDeferred(val value: Boolean) : Command()
        data class CommitPendingPunctuationShortcut(val keyCode: Int) : Command()
        data class CommitPendingPunctuationShortcutOrText(
            val keyCode: Int,
            val text: String
        ) : Command()
        object CommitPendingPunctuation : Command()
        object CancelPendingPunctuation : Command()
        object CancelMultiTapChar : Command()
        object ShowSmartEnglishPunctuationCandidates : Command()
        data class CommitSmartEnglishShortcut(val keyCode: Int) : Command()
        data class CommitSmartEnglishCandidate(
            val appendSpace: Boolean,
            val continuePrediction: Boolean
        ) : Command()
        object CommitSmartEnglishCandidateOrMultiTap : Command()
        data class AppendSmartEnglishDigit(val digit: Int) : Command()
        object ResetSmartEnglishT9 : Command()
        object FlushEnglishLearningWord : Command()
        data class MoveBottomCandidate(
            val delta: Int,
            val fallbackSmartEnglishDelta: Int? = null
        ) : Command()
        data class OffsetBottomCandidatePage(
            val delta: Int
        ) : Command()
        data class ConfirmSmartEnglishCandidate(
            val hasPendingPunctuation: Boolean
        ) : Command()
        data class SmartEnglishDelete(
            val hasPendingPunctuation: Boolean
        ) : Command()
        data class CommitText(val text: String) : Command()
        object HandleReturnKey : Command()
        object SwitchToNextMode : Command()
    }

    private val smartEnglishDigitLongPressFlags = BooleanArray(KeyEvent.KEYCODE_STAR + 1)
    private var smartEnglishOneLongPressTriggered = false
    private var smartEnglishPoundLongPressTriggered = false
    private var pendingSmartEnglishDigitKeyCode: Int? = null
    private var pendingSmartEnglishDigit = -1

    fun resetSmartEnglishPendingDigit() {
        pendingSmartEnglishDigitKeyCode = null
        pendingSmartEnglishDigit = -1
    }

    fun handle(input: PhysicalT9KeyHandler.KeyInput, state: State): Decision? {
        if (state.mode != PhysicalT9KeyHandler.Mode.ENGLISH || !state.isSmartEnglishActive) return null
        return when (input.keyCode) {
            KeyEvent.KEYCODE_1 -> handleSmartEnglishOne(input, state)
            KeyEvent.KEYCODE_POUND -> handleSmartEnglishPound(input, state)
            KeyEvent.KEYCODE_0 -> handleSmartEnglishZero(input, state)
            else -> {
                val digit = PhysicalT9KeyPolicy.t9Digit(input.keyCode)
                when {
                    digit != null && digit in 2..9 ->
                        handleSmartEnglishPredictiveDigit(input, state, digit)
                    isSmartEnglishNavigationOrDeleteKey(input.keyCode) ->
                        handleSmartEnglishNavigationOrDelete(input, state)
                    else -> null
                }
            }
        }
    }

    private fun isSmartEnglishNavigationOrDeleteKey(keyCode: Int): Boolean =
        PhysicalT9KeyPolicy.focusKey(keyCode) != null ||
            keyCode == KeyEvent.KEYCODE_SPACE ||
            PhysicalT9KeyPolicy.isDeleteKey(keyCode)

    private fun isDigitLongPressFlagSet(keyCode: Int): Boolean =
        smartEnglishDigitLongPressFlags.getOrNull(keyCode) == true

    private fun setDigitLongPressFlag(keyCode: Int, value: Boolean) {
        if (keyCode in smartEnglishDigitLongPressFlags.indices) {
            smartEnglishDigitLongPressFlags[keyCode] = value
        }
    }

    private fun handleSmartEnglishOne(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                smartEnglishOneLongPressTriggered = false
                setDigitLongPressFlag(input.keyCode, false)
                Decision(handled = true)
            } else if (!smartEnglishOneLongPressTriggered && state.heldPastLongPressDelay) {
                smartEnglishOneLongPressTriggered = true
                setDigitLongPressFlag(input.keyCode, true)
                Decision(
                    handled = true,
                    commands = when {
                        state.hasPendingPunctuation -> listOf(
                            Command.SetPendingPunctuationOneKeyDeferred(false),
                            Command.CommitPendingPunctuationShortcut(KeyEvent.KEYCODE_1)
                        )
                        state.hasSmartEnglishCandidates -> listOf(
                            Command.CommitSmartEnglishShortcut(KeyEvent.KEYCODE_1)
                        )
                        else -> listOf(
                            Command.CancelPendingPunctuation,
                            Command.CancelMultiTapChar,
                            Command.CommitText("1")
                        )
                    }
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            if (smartEnglishOneLongPressTriggered) {
                smartEnglishOneLongPressTriggered = false
                setDigitLongPressFlag(input.keyCode, false)
                Decision(handled = true)
            } else {
                setDigitLongPressFlag(input.keyCode, false)
                Decision(
                    handled = true,
                    commands = smartEnglishOneShortPressCommands(state)
                )
            }
        }
        else -> null
    }

    private fun smartEnglishOneShortPressCommands(state: State): List<Command> = when {
        state.pendingPunctuationOneKeyDeferred &&
            state.pendingPunctuationSet == PhysicalT9KeyHandler.PunctuationSet.ENGLISH ->
            listOf(
                Command.ShowSmartEnglishPunctuationCandidates,
                Command.SetPendingPunctuationOneKeyDeferred(false)
            )
        state.hasPendingPunctuation -> emptyList()
        state.hasSmartEnglishCandidates -> listOf(
            Command.CommitSmartEnglishCandidate(
                appendSpace = false,
                continuePrediction = false
            ),
            Command.ShowSmartEnglishPunctuationCandidates
        )
        else -> listOf(Command.ShowSmartEnglishPunctuationCandidates)
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
                        Command.CommitSmartEnglishCandidateOrMultiTap,
                        Command.CommitPendingPunctuation,
                        Command.CommitText(" "),
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
                pendingSmartEnglishDigitKeyCode = input.keyCode
                pendingSmartEnglishDigit = digit
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
            if (pendingSmartEnglishDigitKeyCode != input.keyCode) {
                return Decision(handled = true)
            }
            val pendingDigit = pendingSmartEnglishDigit
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

    private fun handleSmartEnglishNavigationOrDelete(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? {
        if (!state.hasSmartEnglishCandidates && !state.hasPendingPunctuation) return null
        if (input.action != KeyEvent.ACTION_DOWN) return null
        val focusKey = PhysicalT9KeyPolicy.focusKey(input.keyCode)
            ?: if (input.keyCode == KeyEvent.KEYCODE_SPACE) PhysicalT9KeyPolicy.FocusKey.OK else null
        val command = when (focusKey) {
            PhysicalT9KeyPolicy.FocusKey.LEFT -> Command.MoveBottomCandidate(
                delta = -1,
                fallbackSmartEnglishDelta = (-1).takeUnless { state.hasPendingPunctuation }
            )
            PhysicalT9KeyPolicy.FocusKey.RIGHT -> Command.MoveBottomCandidate(
                delta = 1,
                fallbackSmartEnglishDelta = 1.takeUnless { state.hasPendingPunctuation }
            )
            PhysicalT9KeyPolicy.FocusKey.UP -> Command.OffsetBottomCandidatePage(
                delta = -1
            )
            PhysicalT9KeyPolicy.FocusKey.DOWN -> Command.OffsetBottomCandidatePage(
                delta = 1
            )
            PhysicalT9KeyPolicy.FocusKey.OK -> Command.ConfirmSmartEnglishCandidate(
                state.hasPendingPunctuation
            )
            null -> if (PhysicalT9KeyPolicy.isDeleteKey(input.keyCode)) {
                Command.SmartEnglishDelete(state.hasPendingPunctuation)
            } else {
                return null
            }
        }
        return Decision(
            handled = true,
            commands = listOf(command),
            consumedKeyUp = input.keyCode
        )
    }

    private fun handleSmartEnglishPound(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                smartEnglishPoundLongPressTriggered = false
                Decision(handled = true)
            } else if (!smartEnglishPoundLongPressTriggered && state.heldPastLongPressDelay) {
                smartEnglishPoundLongPressTriggered = true
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
            if (smartEnglishPoundLongPressTriggered) {
                smartEnglishPoundLongPressTriggered = false
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
}

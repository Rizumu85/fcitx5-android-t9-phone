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
        val hasSmartEnglishDigits: Boolean,
        val hasSmartEnglishCandidates: Boolean,
        val hasMultiTapPendingChar: Boolean,
        val hasTopPinyinCandidates: Boolean,
        val hasBottomCandidateRow: Boolean,
        val candidateFocus: PhysicalT9KeyHandler.CandidateFocus,
        val heldPastLongPressDelay: Boolean
    )

    data class Decision(
        val handled: Boolean,
        val commands: List<Command> = emptyList(),
        val consumedKeyUp: Int? = null
    )

    enum class BottomCandidateFallback {
        NONE,
        PENDING_PUNCTUATION,
        SMART_ENGLISH
    }

    sealed class Command {
        data class SetPendingPunctuationOneKeyDeferred(val value: Boolean) : Command()
        data class CommitPendingPunctuationShortcut(val keyCode: Int) : Command()
        data class CommitPendingPunctuationShortcutOrText(
            val keyCode: Int,
            val text: String
        ) : Command()
        object CommitPendingPunctuation : Command()
        object CancelPendingPunctuation : Command()
        object HandleChinesePunctuationKey : Command()
        object CancelMultiTapChar : Command()
        object ShowSmartEnglishPunctuationCandidates : Command()
        data class HandleMultiTapKey(val keyCode: Int) : Command()
        object CommitMultiTapChar : Command()
        data class CommitSmartEnglishShortcut(val keyCode: Int) : Command()
        data class CommitSmartEnglishCandidate(
            val appendSpace: Boolean,
            val continuePrediction: Boolean
        ) : Command()
        object CommitSmartEnglishCandidateOrMultiTap : Command()
        object CommitEnglishPendingOrReturn : Command()
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
        data class MoveCandidateFocus(
            val focus: PhysicalT9KeyHandler.CandidateFocus
        ) : Command()
        data class MoveHighlightedPinyin(val delta: Int) : Command()
        object CommitHighlightedPinyin : Command()
        data class CommitBottomCandidate(
            val fallback: BottomCandidateFallback
        ) : Command()
        data class SmartEnglishDelete(
            val hasPendingPunctuation: Boolean
        ) : Command()
        data class CommitText(val text: String) : Command()
        object TogglePendingPunctuationSet : Command()
        object HandleEnglishStarShortPress : Command()
        object HandleEnglishStarLongPress : Command()
        object HandleReturnKey : Command()
        object SwitchToNextMode : Command()
        data class CommitNumberOperatorForKey(
            val keyCode: Int,
            val fallbackDigit: Int
        ) : Command()
        object ShowNumberOperatorHintPanel : Command()
        object CommitLiteralStarInCurrentChineseState : Command()
    }

    private val keyLongPressFlags = BooleanArray(KeyEvent.KEYCODE_STAR + 1)
    private var englishOneLongPressTriggered = false
    private var poundLongPressTriggered = false
    private var pendingSmartEnglishDigitKeyCode: Int? = null
    private var pendingSmartEnglishDigit = -1

    fun resetSmartEnglishPendingDigit() {
        pendingSmartEnglishDigitKeyCode = null
        pendingSmartEnglishDigit = -1
    }

    fun handle(input: PhysicalT9KeyHandler.KeyInput, state: State): Decision? {
        return when (state.mode) {
            PhysicalT9KeyHandler.Mode.ENGLISH -> handleEnglish(input, state)
            PhysicalT9KeyHandler.Mode.NUMBER -> handleNumber(input, state)
            PhysicalT9KeyHandler.Mode.CHINESE -> handleChinese(input, state)
        }
    }

    private fun handleChinese(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when {
        state.hasPendingPunctuation -> handleChinesePendingPunctuation(input, state)
        input.action == KeyEvent.ACTION_DOWN -> handleChineseFocusNavigation(input, state)
        input.action == KeyEvent.ACTION_UP -> handleCompletedChinesePendingPunctuationKeyUp(input)
        else -> null
    }

    private fun handleCompletedChinesePendingPunctuationKeyUp(
        input: PhysicalT9KeyHandler.KeyInput
    ): Decision? {
        // Long-press selection can close the overlay before key-up; the flow still owns that release.
        if (input.keyCode == KeyEvent.KEYCODE_POUND && poundLongPressTriggered) {
            poundLongPressTriggered = false
            return Decision(handled = true)
        }
        if (PhysicalT9KeyPolicy.t9Digit(input.keyCode) != null && isDigitLongPressFlagSet(input.keyCode)) {
            setDigitLongPressFlag(input.keyCode, false)
            return Decision(handled = true)
        }
        return null
    }

    private fun handleChinesePendingPunctuation(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? {
        // Non-control keys should commit the visible punctuation preview, then keep normal app delivery.
        if (input.action == KeyEvent.ACTION_DOWN &&
            !PhysicalT9KeyPolicy.isPendingPunctuationControlKey(input.keyCode)
        ) {
            return Decision(
                handled = false,
                commands = listOf(Command.CommitPendingPunctuation)
            )
        }
        val digit = PhysicalT9KeyPolicy.t9Digit(input.keyCode)
        return when {
            input.keyCode == KeyEvent.KEYCODE_1 -> handleChinesePendingPunctuationOne(input, state)
            input.keyCode == KeyEvent.KEYCODE_0 -> handleChinesePendingPunctuationZero(input, state)
            digit != null && digit in 2..9 -> handleChinesePendingPunctuationDigit(input, state, digit)
            input.keyCode == KeyEvent.KEYCODE_POUND -> handleChinesePendingPunctuationPound(input, state)
            input.keyCode == KeyEvent.KEYCODE_STAR -> handleChinesePendingPunctuationStar(input)
            PhysicalT9KeyPolicy.focusKey(input.keyCode) != null ->
                handleChinesePendingPunctuationFocus(input)
            else -> null
        }
    }

    private fun handleChinesePendingPunctuationOne(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(input.keyCode, false)
                Decision(
                    handled = true,
                    commands = listOf(Command.SetPendingPunctuationOneKeyDeferred(true))
                )
            } else if (!isDigitLongPressFlagSet(input.keyCode) && state.heldPastLongPressDelay) {
                setDigitLongPressFlag(input.keyCode, true)
                Decision(
                    handled = true,
                    commands = listOf(
                        Command.SetPendingPunctuationOneKeyDeferred(false),
                        Command.CommitPendingPunctuationShortcutOrText(input.keyCode, "1")
                    )
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
                    buildList {
                        if (state.pendingPunctuationOneKeyDeferred) {
                            add(Command.HandleChinesePunctuationKey)
                        }
                        add(Command.SetPendingPunctuationOneKeyDeferred(false))
                    }
                }
            )
        }
        else -> null
    }

    private fun handleChinesePendingPunctuationZero(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> handleChinesePendingPunctuationShortcutDown(input, state, "0")
        KeyEvent.ACTION_UP -> handleChinesePendingPunctuationCommitOrSuppressDigitUp(input)
        else -> null
    }

    private fun handleChinesePendingPunctuationDigit(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State,
        digit: Int
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> handleChinesePendingPunctuationShortcutDown(input, state, digit.toString())
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
                        Command.CommitText(digit.toString())
                    )
                }
            )
        }
        else -> null
    }

    private fun handleChinesePendingPunctuationShortcutDown(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State,
        fallbackText: String
    ): Decision {
        return if (input.repeatCount == 0) {
            setDigitLongPressFlag(input.keyCode, false)
            Decision(handled = true)
        } else if (!isDigitLongPressFlagSet(input.keyCode) && input.repeatCount > 0) {
            if (!state.heldPastLongPressDelay) {
                Decision(handled = true)
            } else {
                setDigitLongPressFlag(input.keyCode, true)
                Decision(
                    handled = true,
                    commands = listOf(
                        Command.CommitPendingPunctuationShortcutOrText(input.keyCode, fallbackText)
                    )
                )
            }
        } else {
            Decision(handled = true)
        }
    }

    private fun handleChinesePendingPunctuationCommitOrSuppressDigitUp(
        input: PhysicalT9KeyHandler.KeyInput
    ): Decision {
        val wasLongPress = isDigitLongPressFlagSet(input.keyCode)
        setDigitLongPressFlag(input.keyCode, false)
        return Decision(
            handled = true,
            commands = if (wasLongPress) emptyList() else listOf(Command.CommitPendingPunctuation)
        )
    }

    private fun handleChinesePendingPunctuationPound(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                poundLongPressTriggered = false
                Decision(handled = true)
            } else if (!poundLongPressTriggered && state.heldPastLongPressDelay) {
                poundLongPressTriggered = true
                Decision(
                    handled = true,
                    commands = listOf(
                        Command.CommitPendingPunctuation,
                        Command.SwitchToNextMode
                    )
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            if (poundLongPressTriggered) {
                poundLongPressTriggered = false
                Decision(handled = true)
            } else {
                Decision(
                    handled = true,
                    commands = listOf(Command.CommitPendingPunctuation)
                )
            }
        }
        else -> null
    }

    private fun handleChinesePendingPunctuationStar(
        input: PhysicalT9KeyHandler.KeyInput
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> Decision(handled = true)
        KeyEvent.ACTION_UP -> Decision(
            handled = true,
            commands = listOf(Command.TogglePendingPunctuationSet)
        )
        else -> null
    }

    private fun handleChinesePendingPunctuationFocus(
        input: PhysicalT9KeyHandler.KeyInput
    ): Decision? {
        if (input.action != KeyEvent.ACTION_DOWN) return null
        val command = when (PhysicalT9KeyPolicy.focusKey(input.keyCode)) {
            PhysicalT9KeyPolicy.FocusKey.LEFT -> Command.MoveBottomCandidate(delta = -1)
            PhysicalT9KeyPolicy.FocusKey.RIGHT -> Command.MoveBottomCandidate(delta = 1)
            PhysicalT9KeyPolicy.FocusKey.UP -> Command.OffsetBottomCandidatePage(delta = -1)
            PhysicalT9KeyPolicy.FocusKey.DOWN -> Command.OffsetBottomCandidatePage(delta = 1)
            PhysicalT9KeyPolicy.FocusKey.OK ->
                Command.CommitBottomCandidate(BottomCandidateFallback.PENDING_PUNCTUATION)
            null -> return null
        }
        return Decision(
            handled = true,
            commands = listOf(command),
            consumedKeyUp = input.keyCode
        )
    }

    private fun handleChineseFocusNavigation(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? {
        val focusKey = PhysicalT9KeyPolicy.focusKey(input.keyCode) ?: return null
        if (!state.hasTopPinyinCandidates && !state.hasBottomCandidateRow) return null
        // Visible candidate rows own focus keys, even when a transient row mismatch leaves no command to run.
        val command = when (focusKey) {
            PhysicalT9KeyPolicy.FocusKey.UP -> when {
                state.candidateFocus == PhysicalT9KeyHandler.CandidateFocus.BOTTOM &&
                    state.hasTopPinyinCandidates ->
                    Command.MoveCandidateFocus(PhysicalT9KeyHandler.CandidateFocus.TOP)
                state.hasBottomCandidateRow -> Command.OffsetBottomCandidatePage(delta = -1)
                else -> null
            }
            PhysicalT9KeyPolicy.FocusKey.DOWN -> when (state.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP -> {
                    if (state.hasBottomCandidateRow) {
                        Command.MoveCandidateFocus(PhysicalT9KeyHandler.CandidateFocus.BOTTOM)
                    } else {
                        null
                    }
                }
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM -> {
                    if (state.hasBottomCandidateRow) Command.OffsetBottomCandidatePage(delta = 1) else null
                }
            }
            PhysicalT9KeyPolicy.FocusKey.LEFT -> when (state.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP ->
                    if (state.hasTopPinyinCandidates) Command.MoveHighlightedPinyin(delta = -1) else null
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM ->
                    if (state.hasBottomCandidateRow) Command.MoveBottomCandidate(delta = -1) else null
            }
            PhysicalT9KeyPolicy.FocusKey.RIGHT -> when (state.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP ->
                    if (state.hasTopPinyinCandidates) Command.MoveHighlightedPinyin(delta = 1) else null
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM ->
                    if (state.hasBottomCandidateRow) Command.MoveBottomCandidate(delta = 1) else null
            }
            PhysicalT9KeyPolicy.FocusKey.OK -> when (state.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP ->
                    if (state.hasTopPinyinCandidates) Command.CommitHighlightedPinyin else null
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM ->
                    if (state.hasBottomCandidateRow) {
                        Command.CommitBottomCandidate(BottomCandidateFallback.NONE)
                    } else {
                        null
                    }
            }
        }
        return Decision(
            handled = true,
            commands = listOfNotNull(command),
            consumedKeyUp = input.keyCode
        )
    }

    private fun handleEnglish(
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
        keyLongPressFlags.getOrNull(keyCode) == true

    private fun setDigitLongPressFlag(keyCode: Int, value: Boolean) {
        if (keyCode in keyLongPressFlags.indices) {
            keyLongPressFlags[keyCode] = value
        }
    }

    private fun handleEnglishOne(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? =
        if (state.isSmartEnglishActive) {
            handleSmartEnglishOne(input, state)
        } else {
            handleSimpleEnglishOne(input, state)
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

    private fun handleSmartEnglishOne(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                englishOneLongPressTriggered = false
                setDigitLongPressFlag(input.keyCode, false)
                Decision(handled = true)
            } else if (!englishOneLongPressTriggered && state.heldPastLongPressDelay) {
                englishOneLongPressTriggered = true
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
            if (englishOneLongPressTriggered) {
                englishOneLongPressTriggered = false
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

    private fun handleSimpleEnglishOne(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                englishOneLongPressTriggered = false
                setDigitLongPressFlag(input.keyCode, false)
                Decision(
                    handled = true,
                    commands = listOf(Command.HandleMultiTapKey(input.keyCode))
                )
            } else if (!englishOneLongPressTriggered && state.heldPastLongPressDelay) {
                englishOneLongPressTriggered = true
                setDigitLongPressFlag(input.keyCode, true)
                Decision(
                    handled = true,
                    commands = listOf(
                        Command.CancelPendingPunctuation,
                        Command.CancelMultiTapChar,
                        Command.CommitText("1")
                    )
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            englishOneLongPressTriggered = false
            setDigitLongPressFlag(input.keyCode, false)
            Decision(handled = true, commands = emptyList())
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
                    commands = listOf(
                        Command.CancelMultiTapChar,
                        Command.CommitText("0")
                    )
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
        val smartFocusKey = focusKey
            ?: if (input.keyCode == KeyEvent.KEYCODE_SPACE) PhysicalT9KeyPolicy.FocusKey.OK else null
        val command = when (smartFocusKey) {
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
            PhysicalT9KeyPolicy.FocusKey.OK -> Command.CommitBottomCandidate(
                if (state.hasPendingPunctuation) {
                    BottomCandidateFallback.PENDING_PUNCTUATION
                } else {
                    BottomCandidateFallback.SMART_ENGLISH
                }
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
                poundLongPressTriggered = false
                Decision(handled = true)
            } else if (!poundLongPressTriggered && state.heldPastLongPressDelay) {
                poundLongPressTriggered = true
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
            if (poundLongPressTriggered) {
                poundLongPressTriggered = false
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
                poundLongPressTriggered = false
                Decision(handled = true)
            } else if (!poundLongPressTriggered && state.heldPastLongPressDelay) {
                poundLongPressTriggered = true
                Decision(handled = true, commands = listOf(Command.SwitchToNextMode))
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            if (poundLongPressTriggered) {
                poundLongPressTriggered = false
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
                    commands = listOf(Command.HandleEnglishStarLongPress)
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
                    else -> listOf(Command.HandleEnglishStarShortPress)
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

    private fun handleNumber(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? {
        val digit = PhysicalT9KeyPolicy.t9Digit(input.keyCode)
        return when {
            input.keyCode == KeyEvent.KEYCODE_POUND -> handleNumberPound(input, state)
            digit != null -> handleNumberDigit(input, state, digit)
            input.keyCode == KeyEvent.KEYCODE_STAR -> handleNumberStar(input, state)
            else -> null
        }
    }

    private fun handleNumberPound(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                poundLongPressTriggered = false
                Decision(handled = true)
            } else if (!poundLongPressTriggered && state.heldPastLongPressDelay) {
                poundLongPressTriggered = true
                Decision(handled = true, commands = listOf(Command.SwitchToNextMode))
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            if (poundLongPressTriggered) {
                poundLongPressTriggered = false
                Decision(handled = true)
            } else {
                Decision(handled = true, commands = listOf(Command.CommitEnglishPendingOrReturn))
            }
        }
        else -> null
    }

    private fun handleNumberDigit(
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
                    commands = listOf(
                        Command.CommitNumberOperatorForKey(
                            keyCode = input.keyCode,
                            fallbackDigit = digit
                        )
                    )
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
                    listOf(Command.CommitText(digit.toString()))
                }
            )
        }
        else -> null
    }

    private fun handleNumberStar(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                setDigitLongPressFlag(input.keyCode, false)
                Decision(handled = true)
            } else if (!isDigitLongPressFlagSet(input.keyCode) && state.heldPastLongPressDelay) {
                setDigitLongPressFlag(input.keyCode, true)
                Decision(handled = true, commands = listOf(Command.ShowNumberOperatorHintPanel))
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
                    listOf(Command.CommitLiteralStarInCurrentChineseState)
                }
            )
        }
        else -> null
    }
}

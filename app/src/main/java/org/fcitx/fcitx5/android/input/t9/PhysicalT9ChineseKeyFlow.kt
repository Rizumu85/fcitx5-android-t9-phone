/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyFlow.BottomCandidateFallback
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyFlow.Command
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyFlow.Decision
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyFlow.State

internal class PhysicalT9ChineseKeyFlow(
    private val session: PhysicalT9KeyFlowSession
) {
    fun handle(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when {
        state.hasPendingPunctuation -> handleChinesePendingPunctuation(input, state)
        else -> handleChineseSpecialKey(input, state)
            ?: if (input.action == KeyEvent.ACTION_DOWN) {
                handleChineseFocusNavigation(input, state)
            } else {
                handleCompletedChineseLongPressKeyUp(input)
            }
    }

    private fun handleChineseSpecialKey(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? {
        val digit = PhysicalT9KeyPolicy.t9Digit(input.keyCode)
        return when {
            input.keyCode == KeyEvent.KEYCODE_POUND -> handleChinesePound(input, state)
            input.keyCode == KeyEvent.KEYCODE_STAR -> handleChineseStar(input)
            digit != null -> handleChineseDigit(input, state, digit)
            else -> null
        }
    }

    private fun handleCompletedChineseLongPressKeyUp(
        input: PhysicalT9KeyHandler.KeyInput
    ): Decision? = when {
        input.keyCode == KeyEvent.KEYCODE_POUND && session.poundLongPressTriggered -> {
            session.poundLongPressTriggered = false
            Decision(handled = true)
        }
        PhysicalT9KeyPolicy.t9Digit(input.keyCode) != null &&
            isDigitLongPressFlagSet(input.keyCode) -> {
            setDigitLongPressFlag(input.keyCode, false)
            Decision(handled = true)
        }
        else -> null
    }

    private fun handleChinesePound(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? {
        if (state.hasChineseComposition) return handleChineseComposingPound(input, state)
        return when (input.action) {
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
                    Decision(handled = true, commands = listOf(Command.HandleReturnKey))
                }
            }
            else -> null
        }
    }

    private val State.hasChineseComposition: Boolean
        get() = chineseComposing || compositionKeyCount > 0

    private fun handleChineseComposingPound(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                session.poundLongPressTriggered = false
                // Defer composing # until key-up. If it becomes a long press, Rime never sees the
                // key and cannot open its old expansion list before we switch modes.
                Decision(handled = true)
            } else if (!session.poundLongPressTriggered && state.heldPastLongPressDelay) {
                session.poundLongPressTriggered = true
                Decision(
                    handled = true,
                    commands = listOf(
                        Command.DiscardChineseCompositionForModeSwitch,
                        Command.SwitchToNextMode
                    ),
                    consumedKeyUp = input.keyCode
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
                    commands = listOf(Command.ForwardChineseComposingPoundShortPress)
                )
            }
        }
        else -> null
    }

    private fun handleChineseStar(
        input: PhysicalT9KeyHandler.KeyInput
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> Decision(
            handled = true,
            commands = if (input.repeatCount == 0) {
                listOf(Command.CommitLiteralStarInCurrentChineseState)
            } else {
                emptyList()
            }
        )
        KeyEvent.ACTION_UP -> Decision(handled = true)
        else -> null
    }

    private fun handleChineseDigit(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State,
        digit: Int
    ): Decision? = when (input.keyCode) {
        KeyEvent.KEYCODE_0 -> handleChineseZero(input, state)
        KeyEvent.KEYCODE_1 -> handleChineseOne(input, state)
        else -> if (digit in 2..9) handleChinesePredictiveDigit(input, state, digit) else null
    }

    private fun handleChineseZero(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> handleChineseShortcutDigitDown(
            input = input,
            state = state,
            longPressWhenComposing = listOf(Command.CommitHanziShortcut(input.keyCode)),
            longPressWhenIdle = listOf(Command.CommitText("0")),
            shortPressIdleFallsThrough = false
        )
        KeyEvent.ACTION_UP -> {
            val wasLongPress = isDigitLongPressFlagSet(input.keyCode)
            setDigitLongPressFlag(input.keyCode, false)
            if (!wasLongPress && state.chineseComposing && state.compositionKeyCount == 0) null else Decision(
                handled = true,
                commands = when {
                    wasLongPress -> emptyList()
                    state.compositionKeyCount > 0 -> listOf(
                        when (state.candidateFocus) {
                            PhysicalT9KeyHandler.CandidateFocus.TOP -> Command.CommitHighlightedPinyin
                            PhysicalT9KeyHandler.CandidateFocus.BOTTOM ->
                                Command.CommitBottomCandidate(BottomCandidateFallback.NONE)
                        }
                    )
                    state.chineseComposing -> emptyList()
                    else -> listOf(Command.CommitText(" "))
                }
            )
        }
        else -> null
    }

    private fun handleChineseOne(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> handleChineseShortcutDigitDown(
            input = input,
            state = state,
            longPressWhenComposing = listOf(Command.CommitHanziShortcut(input.keyCode)),
            longPressWhenIdle = listOf(
                Command.SetPendingPunctuationOneKeyDeferred(false),
                Command.CancelPendingPunctuation,
                Command.CommitText("1")
            ),
            shortPressIdleFallsThrough = false,
            shortPressIdleCommands = listOf(Command.SetPendingPunctuationOneKeyDeferred(true))
        )
        KeyEvent.ACTION_UP -> {
            val wasLongPress = isDigitLongPressFlagSet(input.keyCode)
            setDigitLongPressFlag(input.keyCode, false)
            if (!wasLongPress && state.chineseComposing && state.compositionKeyCount == 0) null else Decision(
                handled = true,
                commands = when {
                    wasLongPress -> emptyList()
                    state.compositionKeyCount > 0 -> listOf(Command.ForwardChineseT9SeparatorShortPress)
                    state.pendingPunctuationOneKeyDeferred -> listOf(
                        Command.HandleChinesePunctuationKey,
                        Command.SetPendingPunctuationOneKeyDeferred(false)
                    )
                    else -> emptyList()
                }
            )
        }
        else -> null
    }

    private fun handleChinesePredictiveDigit(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State,
        digit: Int
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> handleChineseShortcutDigitDown(
            input = input,
            state = state,
            longPressWhenComposing = listOf(Command.CommitHanziShortcut(input.keyCode)),
            longPressWhenIdle = listOf(Command.CommitText(digit.toString())),
            shortPressIdleFallsThrough = true
        )
        KeyEvent.ACTION_UP -> {
            val wasLongPress = isDigitLongPressFlagSet(input.keyCode)
            setDigitLongPressFlag(input.keyCode, false)
            when {
                wasLongPress -> Decision(handled = true)
                state.compositionKeyCount > 0 -> Decision(
                    handled = true,
                    commands = listOf(Command.ForwardChineseT9KeyShortPress(input.keyCode))
                )
                else -> null
            }
        }
        else -> null
    }

    private fun handleChineseShortcutDigitDown(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State,
        longPressWhenComposing: List<Command>,
        longPressWhenIdle: List<Command>,
        shortPressIdleFallsThrough: Boolean,
        shortPressIdleCommands: List<Command> = emptyList()
    ): Decision {
        if (input.repeatCount == 0) {
            setDigitLongPressFlag(input.keyCode, false)
            return when {
                state.compositionKeyCount > 0 -> Decision(handled = true)
                state.chineseComposing -> Decision(
                    handled = false,
                    consumedKeyUp = input.keyCode
                )
                shortPressIdleFallsThrough -> Decision(
                    handled = false,
                    consumedKeyUp = input.keyCode
                )
                else -> Decision(handled = true, commands = shortPressIdleCommands)
            }
        }
        if (isDigitLongPressFlagSet(input.keyCode) || !state.heldPastLongPressDelay) {
            return Decision(handled = true)
        }
        setDigitLongPressFlag(input.keyCode, true)
        return Decision(
            handled = true,
            commands = if (state.compositionKeyCount > 0) {
                longPressWhenComposing
            } else {
                longPressWhenIdle
            }
        )
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
                session.poundLongPressTriggered = false
                Decision(handled = true)
            } else if (!session.poundLongPressTriggered && state.heldPastLongPressDelay) {
                session.poundLongPressTriggered = true
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
            if (session.poundLongPressTriggered) {
                session.poundLongPressTriggered = false
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

    private fun isDigitLongPressFlagSet(keyCode: Int): Boolean =
        session.isDigitLongPressFlagSet(keyCode)

    private fun setDigitLongPressFlag(keyCode: Int, value: Boolean) {
        session.setDigitLongPressFlag(keyCode, value)
    }
}

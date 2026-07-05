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
        val commands: List<Command> = emptyList()
    )

    sealed class Command {
        data class SetPendingPunctuationOneKeyDeferred(val value: Boolean) : Command()
        data class CommitPendingPunctuationShortcut(val keyCode: Int) : Command()
        object CommitPendingPunctuation : Command()
        object CancelPendingPunctuation : Command()
        object CancelMultiTapChar : Command()
        object ShowSmartEnglishPunctuationCandidates : Command()
        data class CommitSmartEnglishShortcut(val keyCode: Int) : Command()
        data class CommitSmartEnglishCandidate(
            val appendSpace: Boolean,
            val continuePrediction: Boolean
        ) : Command()
        data class CommitText(val text: String) : Command()
        object HandleReturnKey : Command()
        object SwitchToNextMode : Command()
    }

    private var smartEnglishOneLongPressTriggered = false
    private var smartEnglishPoundLongPressTriggered = false

    fun handle(input: PhysicalT9KeyHandler.KeyInput, state: State): Decision? {
        if (state.mode != PhysicalT9KeyHandler.Mode.ENGLISH || !state.isSmartEnglishActive) return null
        return when (input.keyCode) {
            KeyEvent.KEYCODE_1 -> handleSmartEnglishOne(input, state)
            KeyEvent.KEYCODE_POUND -> handleSmartEnglishPound(input, state)
            else -> null
        }
    }

    private fun handleSmartEnglishOne(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State
    ): Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                smartEnglishOneLongPressTriggered = false
                Decision(handled = true)
            } else if (!smartEnglishOneLongPressTriggered && state.heldPastLongPressDelay) {
                smartEnglishOneLongPressTriggered = true
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
                Decision(handled = true)
            } else {
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

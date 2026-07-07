/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

internal class PhysicalT9NumberKeyFlow(
    private val session: PhysicalT9KeyFlowSession
) {
    fun handle(
        input: PhysicalT9KeyHandler.KeyInput,
        state: PhysicalT9KeyFlow.State
    ): PhysicalT9KeyFlow.Decision? {
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
        state: PhysicalT9KeyFlow.State
    ): PhysicalT9KeyFlow.Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                session.poundLongPressTriggered = false
                PhysicalT9KeyFlow.Decision(handled = true)
            } else if (!session.poundLongPressTriggered && state.heldPastLongPressDelay) {
                session.poundLongPressTriggered = true
                PhysicalT9KeyFlow.Decision(
                    handled = true,
                    commands = listOf(PhysicalT9KeyFlow.Command.SwitchToNextMode)
                )
            } else {
                PhysicalT9KeyFlow.Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            if (session.poundLongPressTriggered) {
                session.poundLongPressTriggered = false
                PhysicalT9KeyFlow.Decision(handled = true)
            } else {
                PhysicalT9KeyFlow.Decision(
                    handled = true,
                    commands = listOf(PhysicalT9KeyFlow.Command.CommitEnglishPendingOrReturn)
                )
            }
        }
        else -> null
    }

    private fun handleNumberDigit(
        input: PhysicalT9KeyHandler.KeyInput,
        state: PhysicalT9KeyFlow.State,
        digit: Int
    ): PhysicalT9KeyFlow.Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                session.setDigitLongPressFlag(input.keyCode, false)
                PhysicalT9KeyFlow.Decision(handled = true)
            } else if (!session.isDigitLongPressFlagSet(input.keyCode) && state.heldPastLongPressDelay) {
                session.setDigitLongPressFlag(input.keyCode, true)
                PhysicalT9KeyFlow.Decision(
                    handled = true,
                    commands = listOf(
                        PhysicalT9KeyFlow.Command.CommitNumberOperatorForKey(
                            keyCode = input.keyCode,
                            fallbackDigit = digit
                        )
                    )
                )
            } else {
                PhysicalT9KeyFlow.Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            val wasLongPress = session.isDigitLongPressFlagSet(input.keyCode)
            session.setDigitLongPressFlag(input.keyCode, false)
            PhysicalT9KeyFlow.Decision(
                handled = true,
                commands = if (wasLongPress) {
                    emptyList()
                } else {
                    listOf(PhysicalT9KeyFlow.Command.CommitText(digit.toString()))
                }
            )
        }
        else -> null
    }

    private fun handleNumberStar(
        input: PhysicalT9KeyHandler.KeyInput,
        state: PhysicalT9KeyFlow.State
    ): PhysicalT9KeyFlow.Decision? = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                session.setDigitLongPressFlag(input.keyCode, false)
                PhysicalT9KeyFlow.Decision(handled = true)
            } else if (!session.isDigitLongPressFlagSet(input.keyCode) && state.heldPastLongPressDelay) {
                session.setDigitLongPressFlag(input.keyCode, true)
                PhysicalT9KeyFlow.Decision(
                    handled = true,
                    commands = listOf(PhysicalT9KeyFlow.Command.ShowNumberOperatorHintPanel)
                )
            } else {
                PhysicalT9KeyFlow.Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            val wasLongPress = session.isDigitLongPressFlagSet(input.keyCode)
            session.setDigitLongPressFlag(input.keyCode, false)
            PhysicalT9KeyFlow.Decision(
                handled = true,
                commands = if (wasLongPress) {
                    emptyList()
                } else {
                    listOf(PhysicalT9KeyFlow.Command.CommitLiteralStarInCurrentChineseState)
                }
            )
        }
        else -> null
    }
}

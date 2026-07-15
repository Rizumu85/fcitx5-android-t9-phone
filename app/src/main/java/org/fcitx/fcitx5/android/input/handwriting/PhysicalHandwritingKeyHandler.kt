/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.view.KeyEvent
import org.fcitx.fcitx5.android.input.t9.PhysicalBottomCandidateKeyFlow
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyPolicy

class PhysicalHandwritingKeyHandler(
    private val longPressDelayMillis: () -> Int,
    private val hasStrokes: () -> Boolean,
    private val hasCandidates: () -> Boolean,
    private val clearPendingCharacter: () -> Boolean,
    private val moveCandidate: (Int) -> Boolean,
    private val offsetPage: (Int) -> Boolean,
    private val commitCurrentCandidate: () -> Boolean,
    private val commitShortcut: (Int) -> Boolean,
    private val performAction: (HandwritingAction) -> Unit
) {
    data class Result(val handled: Boolean, val consumeKeyUp: Int? = null)
    data class KeyInput(
        val action: Int,
        val repeatCount: Int,
        val downTime: Long,
        val eventTime: Long
    )

    private val candidateKeyFlow = PhysicalBottomCandidateKeyFlow(longPressDelayMillis)
    private var pendingPoundKeyCode: Int? = null

    fun handleKeyDown(keyCode: Int, input: KeyInput): Result? {
        if (input.action != KeyEvent.ACTION_DOWN) return null
        if (PhysicalT9KeyPolicy.isDeleteKey(keyCode)) {
            // The dedicated backspace is the escape hatch for an unfinished character: one press
            // cancels the whole handwriting result, while the next reaches normal editor delete.
            if (input.repeatCount == 0 && clearPendingCharacter()) {
                return Result(handled = true, consumeKeyUp = keyCode)
            }
            return null
        }
        candidateKeyFlow.handle(input.toCandidateInput(keyCode))?.let { decision ->
            executeCandidateCommand(decision.command)
            return Result(decision.handled, decision.consumeKeyUp)
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_POUND -> {
                if (input.repeatCount == 0) pendingPoundKeyCode = keyCode
                Result(handled = true)
            }
            KeyEvent.KEYCODE_STAR -> {
                if (input.repeatCount == 0) performAction(HandwritingAction.OPEN_SYMBOLS)
                Result(handled = true, consumeKeyUp = keyCode)
            }
            else -> null
        }
    }

    fun handleKeyUp(keyCode: Int, input: KeyInput): Result? {
        if (input.action != KeyEvent.ACTION_UP) return null
        candidateKeyFlow.handle(input.toCandidateInput(keyCode))?.let { decision ->
            executeCandidateCommand(decision.command)
            return Result(decision.handled, decision.consumeKeyUp)
        }
        if (pendingPoundKeyCode == keyCode) {
            pendingPoundKeyCode = null
            if (hasStrokes()) {
                commitCurrentCandidate()
            } else {
                performAction(HandwritingAction.RETURN)
            }
            return Result(handled = true)
        }
        return null
    }

    fun reset() {
        candidateKeyFlow.reset()
        pendingPoundKeyCode = null
    }

    private fun KeyInput.toCandidateInput(keyCode: Int) = PhysicalBottomCandidateKeyFlow.Input(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        downTime = downTime,
        eventTime = eventTime,
        hasCandidates = hasCandidates()
    )

    private fun executeCandidateCommand(command: PhysicalBottomCandidateKeyFlow.Command?) {
        when (command) {
            is PhysicalBottomCandidateKeyFlow.Command.Move -> moveCandidate(command.delta)
            is PhysicalBottomCandidateKeyFlow.Command.OffsetPage -> offsetPage(command.delta)
            PhysicalBottomCandidateKeyFlow.Command.CommitCurrent -> commitCurrentCandidate()
            is PhysicalBottomCandidateKeyFlow.Command.CommitShortcut -> commitShortcut(command.index)
            is PhysicalBottomCandidateKeyFlow.Command.ShortShortcut ->
                ShortShortcutActions[command.index]?.let(performAction)
            null -> Unit
        }
    }

    private companion object {
        // Short presses mirror the two visible action rails. Long presses remain candidate
        // shortcuts, so one physical keypad can address both layers without timing fallbacks.
        val ShortShortcutActions = mapOf(
            0 to HandwritingAction.OPEN_EMOJI,
            2 to HandwritingAction.DELETE_TEXT,
            3 to HandwritingAction.OPEN_NUMBER,
            5 to HandwritingAction.INSERT_SPACE,
            6 to HandwritingAction.SWITCH_INPUT_MODE,
            8 to HandwritingAction.INSERT_COMMA
        )
    }
}

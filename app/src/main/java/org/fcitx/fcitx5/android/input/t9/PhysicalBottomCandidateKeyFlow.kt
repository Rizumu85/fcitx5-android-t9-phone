/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

/** Physical-key decisions shared by any candidate surface that has only a bottom row. */
class PhysicalBottomCandidateKeyFlow(
    private val longPressDelayMillis: () -> Int
) {
    data class Input(
        val keyCode: Int,
        val action: Int,
        val repeatCount: Int,
        val downTime: Long,
        val eventTime: Long,
        val hasCandidates: Boolean
    )

    data class Decision(
        val handled: Boolean,
        val consumeKeyUp: Int? = null,
        val command: Command? = null
    )

    sealed class Command {
        data class Move(val delta: Int) : Command()
        data class OffsetPage(val delta: Int) : Command()
        data object CommitCurrent : Command()
        data class CommitShortcut(val index: Int) : Command()
        data class ShortShortcut(val index: Int) : Command()
    }

    private var pendingShortcutKeyCode: Int? = null
    private var shortcutLongPressTriggered = false

    fun handle(input: Input): Decision? {
        PhysicalT9KeyPolicy.candidateShortcutIndex(input.keyCode)?.let { shortcutIndex ->
            return handleShortcut(input, shortcutIndex)
        }
        if (input.action != KeyEvent.ACTION_DOWN) return null
        val command = when (PhysicalT9KeyPolicy.focusKey(input.keyCode)) {
            PhysicalT9KeyPolicy.FocusKey.LEFT -> Command.Move(-1)
            PhysicalT9KeyPolicy.FocusKey.RIGHT -> Command.Move(1)
            PhysicalT9KeyPolicy.FocusKey.UP -> Command.OffsetPage(-1)
            PhysicalT9KeyPolicy.FocusKey.DOWN -> Command.OffsetPage(1)
            PhysicalT9KeyPolicy.FocusKey.OK -> Command.CommitCurrent
            null -> return null
        }
        return Decision(handled = true, consumeKeyUp = input.keyCode, command = command)
    }

    private fun handleShortcut(input: Input, shortcutIndex: Int): Decision = when (input.action) {
        KeyEvent.ACTION_DOWN -> {
            if (input.repeatCount == 0) {
                pendingShortcutKeyCode = input.keyCode
                shortcutLongPressTriggered = false
                Decision(handled = true)
            } else if (
                pendingShortcutKeyCode == input.keyCode &&
                !shortcutLongPressTriggered &&
                input.eventTime - input.downTime >= longPressDelayMillis()
            ) {
                shortcutLongPressTriggered = true
                Decision(
                    handled = true,
                    command = Command.CommitShortcut(shortcutIndex).takeIf { input.hasCandidates }
                )
            } else {
                Decision(handled = true)
            }
        }
        KeyEvent.ACTION_UP -> {
            if (pendingShortcutKeyCode != input.keyCode) return Decision(handled = true)
            pendingShortcutKeyCode = null
            val wasLongPress = shortcutLongPressTriggered
            shortcutLongPressTriggered = false
            Decision(
                handled = true,
                // Across Chinese, Smart English, and handwriting, short 0 means “accept the
                // highlighted choice”; long 0 retains shortcut 10.
                command = when {
                    wasLongPress -> null
                    shortcutIndex == 9 && input.hasCandidates -> Command.CommitCurrent
                    else -> Command.ShortShortcut(shortcutIndex)
                }
            )
        }
        else -> Decision(handled = true)
    }

    fun reset() {
        pendingShortcutKeyCode = null
        shortcutLongPressTriggered = false
    }
}

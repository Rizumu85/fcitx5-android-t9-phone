/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.view.KeyEvent
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyPolicy

class PhysicalHandwritingKeyHandler(
    private val longPressDelayMillis: () -> Int,
    private val hasStrokes: () -> Boolean,
    private val hasCandidates: () -> Boolean,
    private val undoStroke: () -> Boolean,
    private val moveCandidate: (Int) -> Boolean,
    private val offsetPage: (Int) -> Boolean,
    private val commitCurrentCandidate: () -> Boolean,
    private val commitShortcut: (Int) -> Boolean,
    private val sendReturn: () -> Unit
) {
    data class Result(val handled: Boolean, val consumeKeyUp: Int? = null)
    data class KeyInput(
        val action: Int,
        val repeatCount: Int,
        val downTime: Long,
        val eventTime: Long
    )

    private var pendingShortcutKeyCode: Int? = null
    private var shortcutLongPressTriggered = false
    private var pendingPoundKeyCode: Int? = null

    fun handleKeyDown(keyCode: Int, input: KeyInput): Result? {
        if (input.action != KeyEvent.ACTION_DOWN) return null
        if (PhysicalT9KeyPolicy.isDeleteKey(keyCode)) {
            if (input.repeatCount == 0 && undoStroke()) {
                return Result(handled = true, consumeKeyUp = keyCode)
            }
            return null
        }
        PhysicalT9KeyPolicy.candidateShortcutIndex(keyCode)?.let { shortcutIndex ->
            if (input.repeatCount == 0) {
                pendingShortcutKeyCode = keyCode
                shortcutLongPressTriggered = false
            } else if (pendingShortcutKeyCode == keyCode &&
                !shortcutLongPressTriggered &&
                input.eventTime - input.downTime >= longPressDelayMillis()
            ) {
                shortcutLongPressTriggered = true
                if (hasCandidates()) commitShortcut(shortcutIndex)
            }
            // Short number presses have no text meaning on the canvas. Keeping them paired here
            // prevents the underlying T9 mode from receiving half of a handwriting interaction.
            return Result(handled = true)
        }
        return when (PhysicalT9KeyPolicy.focusKey(keyCode)) {
            PhysicalT9KeyPolicy.FocusKey.LEFT ->
                Result(handled = true, consumeKeyUp = keyCode).also { moveCandidate(-1) }
            PhysicalT9KeyPolicy.FocusKey.RIGHT ->
                Result(handled = true, consumeKeyUp = keyCode).also { moveCandidate(1) }
            PhysicalT9KeyPolicy.FocusKey.UP ->
                Result(handled = true, consumeKeyUp = keyCode).also { offsetPage(-1) }
            PhysicalT9KeyPolicy.FocusKey.DOWN ->
                Result(handled = true, consumeKeyUp = keyCode).also { offsetPage(1) }
            PhysicalT9KeyPolicy.FocusKey.OK ->
                Result(handled = true, consumeKeyUp = keyCode).also { commitCurrentCandidate() }
            null -> when (keyCode) {
                KeyEvent.KEYCODE_POUND -> {
                    if (input.repeatCount == 0) pendingPoundKeyCode = keyCode
                    Result(handled = true)
                }
                KeyEvent.KEYCODE_STAR -> Result(handled = true, consumeKeyUp = keyCode)
                else -> null
            }
        }
    }

    fun handleKeyUp(keyCode: Int, input: KeyInput): Result? {
        if (input.action != KeyEvent.ACTION_UP) return null
        if (pendingShortcutKeyCode == keyCode) {
            pendingShortcutKeyCode = null
            shortcutLongPressTriggered = false
            return Result(handled = true)
        }
        if (pendingPoundKeyCode == keyCode) {
            pendingPoundKeyCode = null
            if (hasStrokes()) {
                commitCurrentCandidate()
            } else {
                sendReturn()
            }
            return Result(handled = true)
        }
        return null
    }

    fun reset() {
        pendingShortcutKeyCode = null
        shortcutLongPressTriggered = false
        pendingPoundKeyCode = null
    }
}

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

internal object PhysicalT9SelectionMode {
    enum class Surface {
        CHINESE_CANDIDATES,
        PENDING_PUNCTUATION,
        SMART_ENGLISH
    }

    fun handle(
        input: PhysicalT9KeyHandler.KeyInput,
        state: State,
        surface: Surface
    ): Decision? {
        if (input.action != KeyEvent.ACTION_DOWN) return null
        val focusKey = selectionFocusKey(input.keyCode, surface) ?: return null
        if (surface == Surface.CHINESE_CANDIDATES &&
            !state.hasTopReadingCandidates &&
            !state.hasBottomCandidateRow
        ) {
            return null
        }
        val command = when (surface) {
            Surface.CHINESE_CANDIDATES -> chineseCandidateCommand(focusKey, state)
            Surface.PENDING_PUNCTUATION -> bottomOnlyCommand(
                focusKey = focusKey,
                fallback = BottomCandidateFallback.PENDING_PUNCTUATION,
                fallbackSmartEnglish = false
            )
            Surface.SMART_ENGLISH -> bottomOnlyCommand(
                focusKey = focusKey,
                fallback = if (state.hasPendingPunctuation) {
                    BottomCandidateFallback.PENDING_PUNCTUATION
                } else {
                    BottomCandidateFallback.SMART_ENGLISH
                },
                fallbackSmartEnglish = !state.hasPendingPunctuation
            )
        }
        // Product decision: visible T9 candidate rows own physical focus keys even when a transient
        // row mismatch leaves no movement command. This prevents D-pad leakage into the editor.
        return Decision(
            handled = true,
            commands = listOfNotNull(command),
            consumedKeyUp = input.keyCode
        )
    }

    private fun selectionFocusKey(
        keyCode: Int,
        surface: Surface
    ): PhysicalT9KeyPolicy.FocusKey? =
        PhysicalT9KeyPolicy.focusKey(keyCode)
            ?: if (surface == Surface.SMART_ENGLISH && keyCode == KeyEvent.KEYCODE_SPACE) {
                PhysicalT9KeyPolicy.FocusKey.OK
            } else {
                null
            }

    private fun chineseCandidateCommand(
        focusKey: PhysicalT9KeyPolicy.FocusKey,
        state: State
    ): Command? {
        // Scheme transitions can leave one input event carrying TOP focus after a raw scheme has
        // removed that row. Treat the visible bottom row as authoritative instead of swallowing it.
        val effectiveFocus = if (
            state.candidateFocus == PhysicalT9KeyHandler.CandidateFocus.TOP &&
            !state.hasTopReadingCandidates &&
            state.hasBottomCandidateRow
        ) {
            PhysicalT9KeyHandler.CandidateFocus.BOTTOM
        } else {
            state.candidateFocus
        }
        return when (focusKey) {
            PhysicalT9KeyPolicy.FocusKey.UP -> when {
                effectiveFocus == PhysicalT9KeyHandler.CandidateFocus.BOTTOM &&
                    state.hasTopReadingCandidates ->
                    Command.MoveCandidateFocus(PhysicalT9KeyHandler.CandidateFocus.TOP)
                state.hasBottomCandidateRow -> Command.OffsetBottomCandidatePage(delta = -1)
                else -> null
            }
            PhysicalT9KeyPolicy.FocusKey.DOWN -> when (effectiveFocus) {
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
            PhysicalT9KeyPolicy.FocusKey.LEFT -> when (effectiveFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP ->
                    if (state.hasTopReadingCandidates) Command.MoveHighlightedReading(delta = -1) else null
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM ->
                    if (state.hasBottomCandidateRow) Command.MoveBottomCandidate(delta = -1) else null
            }
            PhysicalT9KeyPolicy.FocusKey.RIGHT -> when (effectiveFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP ->
                    if (state.hasTopReadingCandidates) Command.MoveHighlightedReading(delta = 1) else null
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM ->
                    if (state.hasBottomCandidateRow) Command.MoveBottomCandidate(delta = 1) else null
            }
            PhysicalT9KeyPolicy.FocusKey.OK -> when (effectiveFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP ->
                    if (state.hasTopReadingCandidates) Command.CommitHighlightedReading else null
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM ->
                    if (state.hasBottomCandidateRow) {
                        Command.CommitBottomCandidate(BottomCandidateFallback.NONE)
                    } else {
                        null
                    }
            }
        }
    }

    private fun bottomOnlyCommand(
        focusKey: PhysicalT9KeyPolicy.FocusKey,
        fallback: BottomCandidateFallback,
        fallbackSmartEnglish: Boolean
    ): Command =
        when (focusKey) {
            PhysicalT9KeyPolicy.FocusKey.LEFT -> Command.MoveBottomCandidate(
                delta = -1,
                fallbackSmartEnglishDelta = (-1).takeIf { fallbackSmartEnglish }
            )
            PhysicalT9KeyPolicy.FocusKey.RIGHT -> Command.MoveBottomCandidate(
                delta = 1,
                fallbackSmartEnglishDelta = 1.takeIf { fallbackSmartEnglish }
            )
            PhysicalT9KeyPolicy.FocusKey.UP -> Command.OffsetBottomCandidatePage(delta = -1)
            PhysicalT9KeyPolicy.FocusKey.DOWN -> Command.OffsetBottomCandidatePage(delta = 1)
            PhysicalT9KeyPolicy.FocusKey.OK -> Command.CommitBottomCandidate(fallback)
        }
}

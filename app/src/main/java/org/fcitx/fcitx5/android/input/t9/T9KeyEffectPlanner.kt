/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

class T9KeyEffectPlanner {

    data class Snapshot(
        val mode: PhysicalT9KeyHandler.Mode,
        val hasTopPinyinCandidates: Boolean,
        val candidateFocus: PhysicalT9KeyHandler.CandidateFocus
    )

    sealed class Effect(val consumeKeyUp: Boolean = false) {
        object None : Effect()
        data class MoveBottomCandidate(
            val delta: Int,
            val fallbackSmartEnglishDelta: Int? = null,
            val consume: Boolean = true
        ) : Effect(consume)
        data class OffsetBottomCandidatePage(
            val delta: Int,
            val alwaysHandled: Boolean = false,
            val consume: Boolean = true
        ) : Effect(consume)
        data class MoveCandidateFocus(
            val focus: PhysicalT9KeyHandler.CandidateFocus
        ) : Effect(consumeKeyUp = true)
        data class MoveHighlightedPinyin(val delta: Int) : Effect(consumeKeyUp = true)
        object CommitHighlightedPinyin : Effect(consumeKeyUp = true)
        object CommitHighlightedBottomCandidate : Effect(consumeKeyUp = true)
        data class CancelMultiTapChar(val consume: Boolean = false) : Effect(consume)
        data class CancelPendingPunctuation(val consume: Boolean = false) : Effect(consume)
    }

    fun planChineseCandidateFocusNavigation(
        input: PhysicalT9KeyHandler.KeyInput,
        snapshot: Snapshot
    ): Effect {
        if (snapshot.mode != PhysicalT9KeyHandler.Mode.CHINESE) return Effect.None
        if (input.action != KeyEvent.ACTION_DOWN) return Effect.None
        return when (PhysicalT9KeyPolicy.focusKey(input.keyCode)) {
            PhysicalT9KeyPolicy.FocusKey.UP -> when {
                snapshot.hasTopPinyinCandidates ->
                    Effect.MoveCandidateFocus(PhysicalT9KeyHandler.CandidateFocus.TOP)
                snapshot.candidateFocus == PhysicalT9KeyHandler.CandidateFocus.BOTTOM ->
                    Effect.OffsetBottomCandidatePage(delta = -1)
                else -> Effect.None
            }
            PhysicalT9KeyPolicy.FocusKey.DOWN -> when (snapshot.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP ->
                    Effect.MoveCandidateFocus(PhysicalT9KeyHandler.CandidateFocus.BOTTOM)
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM ->
                    Effect.OffsetBottomCandidatePage(delta = 1)
            }
            PhysicalT9KeyPolicy.FocusKey.LEFT -> when (snapshot.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP -> Effect.MoveHighlightedPinyin(delta = -1)
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM -> Effect.MoveBottomCandidate(delta = -1)
            }
            PhysicalT9KeyPolicy.FocusKey.RIGHT -> when (snapshot.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP -> Effect.MoveHighlightedPinyin(delta = 1)
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM -> Effect.MoveBottomCandidate(delta = 1)
            }
            PhysicalT9KeyPolicy.FocusKey.OK -> when (snapshot.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP -> Effect.CommitHighlightedPinyin
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM -> Effect.CommitHighlightedBottomCandidate
            }
            null -> Effect.None
        }
    }
}

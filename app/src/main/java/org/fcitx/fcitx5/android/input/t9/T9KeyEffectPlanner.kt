/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

class T9KeyEffectPlanner {

    data class Snapshot(
        val mode: PhysicalT9KeyHandler.Mode,
        val isSmartEnglishActive: Boolean,
        val hasSmartEnglishDigits: Boolean,
        val hasPendingPunctuation: Boolean,
        val hasMultiTapPendingChar: Boolean,
        val hasTopPinyinCandidates: Boolean,
        val candidateFocus: PhysicalT9KeyHandler.CandidateFocus
    )

    sealed class Effect(val consumeKeyUp: Boolean = false) {
        object None : Effect()
        data class MoveBottomCandidate(
            val delta: Int,
            val fallbackSmartEnglishDelta: Int? = null,
            val handledWhenPendingPunctuation: Boolean = false,
            val consume: Boolean = true
        ) : Effect(consume)
        data class OffsetBottomCandidatePage(
            val delta: Int,
            val handledWhenPendingPunctuation: Boolean = false,
            val alwaysHandled: Boolean = false,
            val consume: Boolean = true
        ) : Effect(consume)
        data class MoveCandidateFocus(
            val focus: PhysicalT9KeyHandler.CandidateFocus
        ) : Effect(consumeKeyUp = true)
        data class MoveHighlightedPinyin(val delta: Int) : Effect(consumeKeyUp = true)
        object CommitHighlightedPinyin : Effect(consumeKeyUp = true)
        data class CommitHighlightedBottomCandidate(
            val handledWhenPendingPunctuation: Boolean = false
        ) : Effect(consumeKeyUp = true)
        data class ConfirmSmartEnglishCandidate(
            val hasPendingPunctuation: Boolean
        ) : Effect(consumeKeyUp = true)
        data class SmartEnglishDelete(
            val hasPendingPunctuation: Boolean,
            val consume: Boolean
        ) : Effect(consume)
        data class CancelMultiTapChar(val consume: Boolean = false) : Effect(consume)
        data class CancelPendingPunctuation(val consume: Boolean = false) : Effect(consume)
    }

    fun planSmartEnglishNavigationKeyDown(
        input: PhysicalT9KeyHandler.KeyInput,
        snapshot: Snapshot
    ): Effect {
        if (!snapshot.isSmartEnglishActive ||
            (!snapshot.hasSmartEnglishDigits && !snapshot.hasPendingPunctuation)
        ) {
            return Effect.None
        }
        if (input.action != KeyEvent.ACTION_DOWN) return Effect.None
        val keyCode = input.keyCode
        val focusKey = PhysicalT9KeyPolicy.focusKey(keyCode)
            ?: if (keyCode == KeyEvent.KEYCODE_SPACE) PhysicalT9KeyPolicy.FocusKey.OK else null
        return when (focusKey) {
            PhysicalT9KeyPolicy.FocusKey.LEFT -> Effect.MoveBottomCandidate(
                delta = -1,
                fallbackSmartEnglishDelta = (-1).takeUnless { snapshot.hasPendingPunctuation }
            )
            PhysicalT9KeyPolicy.FocusKey.RIGHT -> Effect.MoveBottomCandidate(
                delta = 1,
                fallbackSmartEnglishDelta = 1.takeUnless { snapshot.hasPendingPunctuation }
            )
            PhysicalT9KeyPolicy.FocusKey.UP ->
                Effect.OffsetBottomCandidatePage(delta = -1, alwaysHandled = true)
            PhysicalT9KeyPolicy.FocusKey.DOWN ->
                Effect.OffsetBottomCandidatePage(delta = 1, alwaysHandled = true)
            PhysicalT9KeyPolicy.FocusKey.OK ->
                Effect.ConfirmSmartEnglishCandidate(snapshot.hasPendingPunctuation)
            null -> if (PhysicalT9KeyPolicy.isDeleteKey(keyCode)) {
                Effect.SmartEnglishDelete(
                    hasPendingPunctuation = snapshot.hasPendingPunctuation,
                    consume = true
                )
            } else {
                Effect.None
            }
        }
    }

    fun planEnglishDeleteKeyDown(
        input: PhysicalT9KeyHandler.KeyInput,
        snapshot: Snapshot
    ): Effect {
        if (snapshot.mode != PhysicalT9KeyHandler.Mode.ENGLISH) return Effect.None
        if (input.action != KeyEvent.ACTION_DOWN) return Effect.None
        if (!PhysicalT9KeyPolicy.isDeleteKey(input.keyCode)) return Effect.None
        return when {
            snapshot.hasSmartEnglishDigits ->
                Effect.SmartEnglishDelete(hasPendingPunctuation = false, consume = true)
            snapshot.hasMultiTapPendingChar -> Effect.CancelMultiTapChar()
            snapshot.hasPendingPunctuation -> Effect.CancelPendingPunctuation()
            else -> Effect.None
        }
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
                    Effect.OffsetBottomCandidatePage(
                        delta = -1,
                        handledWhenPendingPunctuation = true
                    )
                else -> Effect.None
            }
            PhysicalT9KeyPolicy.FocusKey.DOWN -> when (snapshot.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP ->
                    Effect.MoveCandidateFocus(PhysicalT9KeyHandler.CandidateFocus.BOTTOM)
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM ->
                    Effect.OffsetBottomCandidatePage(
                        delta = 1,
                        handledWhenPendingPunctuation = true
                    )
            }
            PhysicalT9KeyPolicy.FocusKey.LEFT -> when (snapshot.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP -> Effect.MoveHighlightedPinyin(delta = -1)
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM -> Effect.MoveBottomCandidate(
                    delta = -1,
                    handledWhenPendingPunctuation = true
                )
            }
            PhysicalT9KeyPolicy.FocusKey.RIGHT -> when (snapshot.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP -> Effect.MoveHighlightedPinyin(delta = 1)
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM -> Effect.MoveBottomCandidate(
                    delta = 1,
                    handledWhenPendingPunctuation = true
                )
            }
            PhysicalT9KeyPolicy.FocusKey.OK -> when (snapshot.candidateFocus) {
                PhysicalT9KeyHandler.CandidateFocus.TOP -> Effect.CommitHighlightedPinyin
                PhysicalT9KeyHandler.CandidateFocus.BOTTOM ->
                    Effect.CommitHighlightedBottomCandidate(handledWhenPendingPunctuation = true)
            }
            null -> Effect.None
        }
    }
}

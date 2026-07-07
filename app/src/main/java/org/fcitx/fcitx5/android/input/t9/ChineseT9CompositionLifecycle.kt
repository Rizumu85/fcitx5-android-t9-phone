/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent

class ChineseT9CompositionLifecycle(
    private val session: ChineseT9CompositionSession,
    private val presentationCache: ChineseT9PresentationSnapshotCache = ChineseT9PresentationSnapshotCache()
) {

    enum class InputState {
        IDLE,
        COMPOSING
    }

    enum class ForwardedKeyAction {
        NONE,
        REFRESH_AFTER_ENGINE_CANDIDATES,
        HIDE_CANDIDATE_UI_IMMEDIATELY
    }

    fun inputState(hasComposingText: Boolean): InputState =
        if (hasComposingText) InputState.COMPOSING else InputState.IDLE

    fun clearCompositionState() {
        session.clear()
        presentationCache.reset()
    }

    fun hasCompositionState(): Boolean =
        session.hasState()

    fun shouldClearFromEditorTap(isActive: Boolean, state: InputState): Boolean =
        isActive && (state == InputState.COMPOSING || hasCompositionState())

    fun shouldClearHiddenComposition(
        isActive: Boolean,
        hasPendingPunctuation: Boolean
    ): Boolean =
        isActive && !hasPendingPunctuation && session.keyCount() <= 0

    fun shouldResetEngineForLiteralStar(isChineseMode: Boolean, state: InputState): Boolean =
        isChineseMode && state == InputState.COMPOSING

    fun shouldReopenLastResolvedSegment(isActive: Boolean): Boolean =
        isActive && session.shouldReopenLastResolvedSegment()

    fun appendSeparatorForShortcut() {
        session.appendSeparator()
    }

    fun backspaceFromVirtualKey() {
        session.backspace()
    }

    fun handleForwardedKeyDown(keyCode: Int): ForwardedKeyAction {
        val hadComposition = session.keyCount() > 0
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                session.backspace()
                when {
                    !hadComposition -> ForwardedKeyAction.NONE
                    session.keyCount() <= 0 -> ForwardedKeyAction.HIDE_CANDIDATE_UI_IMMEDIATELY
                    else -> ForwardedKeyAction.REFRESH_AFTER_ENGINE_CANDIDATES
                }
            }
            KeyEvent.KEYCODE_1 -> {
                session.appendSeparator()
                ForwardedKeyAction.REFRESH_AFTER_ENGINE_CANDIDATES
            }
            else -> {
                val digit = PhysicalT9KeyPolicy.t9Digit(keyCode)
                    ?.takeIf { it in 2..9 }
                    ?: return ForwardedKeyAction.NONE
                session.appendDigit('0' + digit)
                ForwardedKeyAction.REFRESH_AFTER_ENGINE_CANDIDATES
            }
        }
    }

    fun getOrBuildPresentation(
        key: ChineseT9PresentationSnapshotKey,
        builder: () -> T9PresentationState
    ): T9PresentationState =
        presentationCache.getOrBuild(key, builder)
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9PunctuationCoordinator(
    session: T9PunctuationSession = T9PunctuationSession(),
    private val clearTransientInputUiState: () -> Unit,
    private val refreshUi: () -> Unit,
    private val cancelTimeout: () -> Unit,
    private val commitText: (String) -> Unit
) {
    private val lifecycle = T9PunctuationLifecycle(session)

    val isPending: Boolean
        get() = lifecycle.isPending

    val pendingText: String?
        get() = lifecycle.pendingText

    fun showChineseCandidates() {
        apply(lifecycle.showChineseCandidates())
    }

    fun showEnglishCandidates() {
        apply(lifecycle.showEnglishCandidates())
    }

    fun paged(): FcitxEvent.PagedCandidateEvent.Data? =
        lifecycle.paged()

    fun selectAndCommitCandidate(index: Int): Boolean {
        return apply(lifecycle.selectAndCommitCandidate(index))
    }

    fun previewCandidate(index: Int): Boolean {
        return apply(lifecycle.previewCandidate(index))
    }

    fun toggleSet(): Boolean {
        return apply(lifecycle.toggleSet())
    }

    fun commit(): Boolean {
        return apply(lifecycle.commit())
    }

    fun cancel(): Boolean {
        return apply(lifecycle.cancel())
    }

    private fun apply(result: T9PunctuationLifecycle.Result): Boolean {
        result.effects.forEach { effect ->
            when (effect) {
                T9PunctuationLifecycle.Effect.ClearTransientInputUiState ->
                    clearTransientInputUiState()
                T9PunctuationLifecycle.Effect.RefreshUi ->
                    refreshUi()
                T9PunctuationLifecycle.Effect.CancelTimeout ->
                    cancelTimeout()
                is T9PunctuationLifecycle.Effect.CommitText ->
                    commitText(effect.text)
            }
        }
        return result.handled
    }
}

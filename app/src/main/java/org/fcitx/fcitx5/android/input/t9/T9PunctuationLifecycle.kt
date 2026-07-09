/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9PunctuationLifecycle(
    private val session: T9PunctuationSession = T9PunctuationSession()
) {
    sealed class Effect {
        data object ClearTransientInputUiState : Effect()
        data object RefreshUi : Effect()
        data object CancelTimeout : Effect()
        data class CommitText(val text: String) : Effect()
    }

    data class Result(
        val handled: Boolean,
        val effects: List<Effect> = emptyList()
    )

    val isPending: Boolean
        get() = session.isPending

    val pendingText: String?
        get() = session.pendingText

    fun showChineseCandidates(): Result {
        session.showChineseCandidates()
        return showPending()
    }

    fun showEnglishCandidates(): Result {
        session.showEnglishCandidates()
        return showPending()
    }

    fun paged(): FcitxEvent.PagedCandidateEvent.Data? =
        session.paged()

    fun selectAndCommitCandidate(index: Int): Result {
        session.selectCandidate(index) ?: return Result(handled = false)
        return commit()
    }

    fun previewCandidate(index: Int): Result {
        session.selectCandidate(index) ?: return Result(handled = false)
        return Result(
            handled = true,
            effects = listOf(Effect.RefreshUi)
        )
    }

    fun toggleSet(): Result {
        session.toggleSet() ?: return Result(handled = false)
        return showPending()
    }

    fun commit(): Result {
        val punctuation = session.commit()
        return if (punctuation == null) {
            Result(
                handled = false,
                effects = listOf(Effect.CancelTimeout)
            )
        } else {
            Result(
                handled = true,
                effects = listOf(
                    Effect.CancelTimeout,
                    Effect.CommitText(punctuation),
                    Effect.RefreshUi
                )
            )
        }
    }

    fun cancel(): Result {
        val canceled = session.cancel()
        return if (!canceled) {
            Result(
                handled = false,
                effects = listOf(Effect.CancelTimeout)
            )
        } else {
            Result(
                handled = true,
                effects = listOf(
                    Effect.CancelTimeout,
                    Effect.RefreshUi
                )
            )
        }
    }

    private fun showPending(prefix: List<Effect> = emptyList()): Result =
        Result(
            handled = true,
            effects = prefix + listOf(
                Effect.ClearTransientInputUiState,
                Effect.RefreshUi,
                Effect.CancelTimeout
            )
        )
}

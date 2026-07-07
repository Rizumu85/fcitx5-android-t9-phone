/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class ChineseT9CandidateLoadingState {
    enum class State {
        IDLE,
        WAITING_FOR_ENGINE
    }

    var state: State = State.IDLE
        private set

    fun reset() {
        state = State.IDLE
    }

    fun startIfNeeded(chineseT9Active: Boolean, digitSequence: String) {
        state = if (chineseT9Active && digitSequence.any { it in '2'..'9' }) {
            State.WAITING_FOR_ENGINE
        } else {
            State.IDLE
        }
    }

    fun onEngineCandidates(
        data: FcitxEvent.PagedCandidateEvent.Data,
        digitSequence: String
    ) {
        val hasComposition = digitSequence.any { it in '2'..'9' }
        if (!hasComposition || ChineseT9CandidateFreshness.matchesDigitSequence(data, digitSequence)) {
            state = State.IDLE
        }
    }

    fun shouldWaitForCandidates(
        chineseT9Active: Boolean,
        compositionKeyCount: Int,
        hasPendingPunctuation: Boolean,
        pendingPinyinSelection: Boolean,
        rawCandidatesEmpty: Boolean
    ): Boolean =
        chineseT9Active &&
            compositionKeyCount > 0 &&
            !hasPendingPunctuation &&
            !pendingPinyinSelection &&
            (state == State.WAITING_FOR_ENGINE || rawCandidatesEmpty)
}

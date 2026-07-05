/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9PunctuationCoordinator(
    private val session: T9PunctuationSession = T9PunctuationSession(),
    private val clearTransientInputUiState: () -> Unit,
    private val refreshUi: () -> Unit,
    private val cancelTimeout: () -> Unit,
    private val commitText: (String) -> Unit
) {
    val isPending: Boolean
        get() = session.isPending

    val oneKeyDeferred: Boolean
        get() = session.oneKeyDeferred

    val pendingText: String?
        get() = session.pendingText

    val physicalSet: PhysicalT9KeyHandler.PunctuationSet
        get() = when (session.set) {
            T9PunctuationSession.Set.CHINESE -> PhysicalT9KeyHandler.PunctuationSet.CHINESE
            T9PunctuationSession.Set.ENGLISH -> PhysicalT9KeyHandler.PunctuationSet.ENGLISH
        }

    fun setOneKeyDeferred(value: Boolean) {
        session.setOneKeyDeferred(value)
    }

    fun showEnglishCandidates() {
        session.showEnglishCandidates()
        showPending()
    }

    fun paged(): FcitxEvent.PagedCandidateEvent.Data? =
        session.paged()

    fun selectAndCommitCandidate(index: Int): Boolean {
        session.selectCandidate(index) ?: return false
        return commit()
    }

    fun previewCandidate(index: Int): Boolean {
        session.selectCandidate(index) ?: return false
        refreshUi()
        return true
    }

    fun handleChineseKey(hasCompositionKeys: Boolean): Boolean {
        cancelTimeout()
        session.handleChineseKey(hasCompositionKeys) ?: return true
        showPending()
        return true
    }

    fun toggleSet(): Boolean {
        session.toggleSet() ?: return false
        showPending()
        return true
    }

    fun commit(): Boolean {
        cancelTimeout()
        val punctuation = session.commit() ?: return false
        commitText(punctuation)
        refreshUi()
        return true
    }

    fun cancel(): Boolean {
        cancelTimeout()
        if (!session.cancel()) return false
        refreshUi()
        return true
    }

    private fun showPending() {
        clearTransientInputUiState()
        refreshUi()
        cancelTimeout()
    }
}

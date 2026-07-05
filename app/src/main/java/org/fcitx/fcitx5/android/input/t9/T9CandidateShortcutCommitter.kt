/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class T9CandidateShortcutCommitter(
    private val commitPendingPunctuationIndex: (Int) -> Boolean,
    private val commitHanziIndex: (Int) -> Boolean,
    private val commitSmartEnglishIndex: (Int) -> Boolean
) {
    fun commitPendingPunctuationKey(keyCode: Int): Boolean =
        commitByKeyCode(keyCode, commitPendingPunctuationIndex)

    fun commitHanziKey(keyCode: Int): Boolean =
        commitByKeyCode(keyCode, commitHanziIndex)

    fun commitSmartEnglishKey(keyCode: Int): Boolean =
        commitByKeyCode(keyCode, commitSmartEnglishIndex)

    private fun commitByKeyCode(
        keyCode: Int,
        commitIndex: (Int) -> Boolean
    ): Boolean {
        val index = PhysicalT9KeyPolicy.candidateShortcutIndex(keyCode) ?: return false
        return commitIndex(index)
    }
}

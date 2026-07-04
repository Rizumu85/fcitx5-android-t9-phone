/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation

data class T9PreeditSnapshot(val signature: String)

data class T9CandidatePageSnapshot(
    val contentSignature: String,
    val cursorSignature: String
)

class T9CandidatePagerSnapshot(
    val characterBudget: Any,
    val layoutHint: FcitxEvent.PagedCandidateEvent.LayoutHint,
    val hasPrev: Boolean,
    val hasNext: Boolean,
    val candidates: Array<FcitxEvent.Candidate>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is T9CandidatePagerSnapshot) return false
        if (characterBudget != other.characterBudget) return false
        if (layoutHint != other.layoutHint) return false
        if (hasPrev != other.hasPrev) return false
        if (hasNext != other.hasNext) return false
        return candidates.contentEquals(other.candidates)
    }

    override fun hashCode(): Int {
        var result = characterBudget.hashCode()
        result = 31 * result + layoutHint.hashCode()
        result = 31 * result + hasPrev.hashCode()
        result = 31 * result + hasNext.hashCode()
        result = 31 * result + candidates.contentHashCode()
        return result
    }
}

data class T9PinyinSnapshot(val signature: String)

data class T9VisibilitySnapshot(
    val shouldShow: Boolean,
    val preferAboveCursorAnchor: Boolean
)

object T9CandidateSnapshots {
    fun preedit(data: FcitxEvent.InputPanelEvent.Data): T9PreeditSnapshot =
        T9PreeditSnapshot(
            buildString {
                append(data.preedit).append('\n')
                append(data.auxUp).append('\n')
                append(data.auxDown)
            }
        )

    fun renderCandidates(
        data: FcitxEvent.PagedCandidateEvent.Data,
        orientation: FloatingCandidatesOrientation,
        showShortcutLabels: Boolean
    ): T9CandidatePageSnapshot =
        T9CandidatePageSnapshot(
            contentSignature = pagedContent(
                data = data,
                orientation = orientation,
                showShortcutLabels = showShortcutLabels
            ),
            cursorSignature = data.cursorIndex.toString()
        )

    fun pagerSnapshot(
        data: FcitxEvent.PagedCandidateEvent.Data,
        characterBudget: Any
    ): T9CandidatePagerSnapshot =
        T9CandidatePagerSnapshot(
            characterBudget = if (characterBudget is Int) {
                T9CandidateBudget.normalizedBudget(characterBudget)
            } else {
                characterBudget
            },
            layoutHint = data.layoutHint,
            hasPrev = data.hasPrev,
            hasNext = data.hasNext,
            candidates = data.candidates
        )

    fun pinyin(candidates: List<String>, useT9: Boolean): T9PinyinSnapshot =
        T9PinyinSnapshot(
            buildString {
                append(useT9).append('|')
                candidates.forEach {
                    append(it).append('\n')
                }
            }
        )

    fun visibility(
        shouldShow: Boolean,
        preferAboveCursorAnchor: Boolean
    ): T9VisibilitySnapshot =
        T9VisibilitySnapshot(
            shouldShow = shouldShow,
            preferAboveCursorAnchor = preferAboveCursorAnchor
        )

    private fun pagedContent(
        data: FcitxEvent.PagedCandidateEvent.Data,
        orientation: FloatingCandidatesOrientation?,
        showShortcutLabels: Boolean?,
        prefix: String = ""
    ): String =
        buildString {
            append(prefix)
            orientation?.let { append(it.name).append('|') }
            showShortcutLabels?.let { append(it).append('|') }
            append(data.layoutHint.name).append('|')
            append(data.hasPrev).append('|').append(data.hasNext).append('\n')
            data.candidates.forEach {
                if (showShortcutLabels == true) {
                    append(it.text).append('\n')
                    return@forEach
                }
                append(it.label).append('|')
                append(it.text).append('|')
                append(it.comment).append('\n')
            }
        }
}

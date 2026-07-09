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

data class T9PinyinSnapshot(val signature: String)

data class T9VisibilitySnapshot(
    val shouldShow: Boolean,
    val preferAboveCursorAnchor: Boolean
)

object T9CandidateSnapshots {
    fun preedit(data: FcitxEvent.InputPanelEvent.Data, reservePreeditRow: Boolean): T9PreeditSnapshot =
        T9PreeditSnapshot(
            buildString {
                append(reservePreeditRow).append('\n')
                append(data.preedit).append('\n')
                append(data.auxUp).append('\n')
                append(data.auxDown)
            }
        )

    fun renderCandidates(
        data: FcitxEvent.PagedCandidateEvent.Data,
        orientation: FloatingCandidatesOrientation,
        showShortcutLabels: Boolean,
        shortcutStyle: T9ShortcutCandidateStyle
    ): T9CandidatePageSnapshot =
        T9CandidatePageSnapshot(
            contentSignature = pagedContent(
                data = data,
                orientation = orientation,
                showShortcutLabels = showShortcutLabels,
                prefix = "${shortcutStyle.name}|"
            ),
            cursorSignature = data.cursorIndex.toString()
        )

    fun pagerContent(
        data: FcitxEvent.PagedCandidateEvent.Data,
        characterBudget: Int,
        widthBudget: T9CandidateWidthBudget? = null
    ): String =
        pagedContent(
            data = data,
            orientation = null,
            showShortcutLabels = null,
            prefix = "$characterBudget|${widthBudget?.signature.orEmpty()}|"
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
                append(it.label).append('|')
                append(it.text).append('|')
                append(it.comment).append('\n')
            }
        }
}

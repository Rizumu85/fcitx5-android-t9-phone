/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText

data class SmartEnglishSnapshot(
    val publicationKey: String,
    val contentKey: String,
    val inputRevision: Long,
    val dictionaryGeneration: Long,
    val predictionGeneration: Long,
    val paged: FcitxEvent.PagedCandidateEvent.Data?,
    val previewText: String?,
    val reserveTopReadingRow: Boolean
) {
    fun toUiSnapshot(formatText: (String) -> FormattedText?): SmartEnglishUiSnapshot =
        SmartEnglishUiSnapshot(
            publicationKey = publicationKey,
            contentKey = contentKey,
            paged = paged,
            presentation = previewText?.let { preview ->
                T9PresentationState(
                    topReading = formatText(preview),
                    readingOptions = emptyList(),
                    reserveTopReadingRow = reserveTopReadingRow
                )
            }
        )
}

data class SmartEnglishUiSnapshot(
    val publicationKey: String,
    val contentKey: String,
    val paged: FcitxEvent.PagedCandidateEvent.Data?,
    val presentation: T9PresentationState?
)

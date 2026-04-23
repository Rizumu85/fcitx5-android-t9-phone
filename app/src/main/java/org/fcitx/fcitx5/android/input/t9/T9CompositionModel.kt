/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FormattedText

data class T9ResolvedSegment(
    val pinyin: String,
    val sourceDigits: String,
)

data class T9PendingSelection(
    val segment: T9ResolvedSegment,
    val remainingDigits: String,
)

data class T9CompositionModel(
    val resolvedSegments: List<T9ResolvedSegment> = emptyList(),
    val unresolvedDigits: String = "",
    val rawPreedit: String = "",
    val pendingSelection: T9PendingSelection? = null,
) {
    val resolvedReading: String
        get() = resolvedSegments.joinToString(" ") { it.pinyin }

    val hasResolvedSegments: Boolean
        get() = resolvedSegments.isNotEmpty()
}

data class T9PresentationState(
    val topReading: FormattedText?,
    val pinyinOptions: List<String>,
) {
    val pinyinRowVisible: Boolean
        get() = pinyinOptions.isNotEmpty()
}

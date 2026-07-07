/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

data class T9ShortcutCandidateLayout(
    val maxCandidateWidthPx: Int,
    val rowWidthPx: Int,
    val edgePaddingPx: Int,
    val maxRowWidthPx: Int,
    val trailingPaddingPx: Int
)

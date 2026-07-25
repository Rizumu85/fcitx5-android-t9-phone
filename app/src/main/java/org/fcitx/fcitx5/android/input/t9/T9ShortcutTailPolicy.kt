/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9ShortcutTailPolicy {
    fun edgeAlignsCandidateToBubbleTail(
        isCandidate: Boolean,
        isLastVisibleItem: Boolean,
        preserveUniformMinimumWidth: Boolean = false
    ): Boolean = isCandidate && isLastVisibleItem && !preserveUniformMinimumWidth
}

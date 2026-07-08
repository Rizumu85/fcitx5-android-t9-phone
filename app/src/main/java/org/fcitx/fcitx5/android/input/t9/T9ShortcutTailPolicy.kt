/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlin.math.ceil
import kotlin.math.max

object T9ShortcutTailPolicy {
    fun edgeAlignsCandidateToBubbleTail(
        isCandidate: Boolean,
        isLastVisibleItem: Boolean
    ): Boolean = isCandidate && isLastVisibleItem

    fun plannedToolbarWidthPx(
        candidateContentWidthPx: Int,
        tailScaleOverflowPx: Int,
        edgePaddingPx: Int,
        trailingPaddingPx: Int,
        maxRowWidthPx: Int
    ): Int {
        val targetWidth = candidateContentWidthPx.coerceAtLeast(0) +
            tailScaleOverflowPx.coerceAtLeast(0) +
            edgePaddingPx.coerceAtLeast(0) * 2 +
            trailingPaddingPx.coerceAtLeast(0)
        return maxRowWidthPx.takeIf { it > 0 }
            ?.let { targetWidth.coerceAtMost(it) }
            ?: targetWidth
    }

    fun stabilizedToolbarWidthPx(
        naturalWidthPx: Int,
        lastChildMeasuredRightPx: Int?,
        lastChildMeasuredWidthPx: Int?,
        lastChildScaleX: Float,
        edgePaddingPx: Int,
        trailingPaddingPx: Int,
        maxRowWidthPx: Int
    ): Int {
        val naturalWidth = naturalWidthPx.coerceAtLeast(0)
        val childRight = lastChildMeasuredRightPx ?: return naturalWidth
        val childWidth = lastChildMeasuredWidthPx ?: return naturalWidth
        val scaleOverflow = ceil(childWidth * (lastChildScaleX - 1f).coerceAtLeast(0f) / 2f)
            .toInt()
            .coerceAtLeast(0)
        val targetWidth = childRight +
            scaleOverflow +
            edgePaddingPx.coerceAtLeast(0) +
            trailingPaddingPx.coerceAtLeast(0)
        val cappedWidth = max(naturalWidth, targetWidth)
        return maxRowWidthPx.takeIf { it > 0 }
            ?.let { cappedWidth.coerceAtMost(it) }
            ?: cappedWidth
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

internal data class HandwritingActionRailSizing(
    val buttonSizePx: Int,
    val verticalMarginPx: Int
) {
    fun occupiedHeight(buttonCount: Int): Int =
        buttonCount * (buttonSizePx + verticalMarginPx * 2)
}

internal object HandwritingActionRailLayoutPolicy {
    fun resolve(
        availableHeightPx: Int,
        buttonCount: Int,
        preferredButtonSizePx: Int,
        preferredMarginPx: Int,
        minimumButtonSizePx: Int,
        minimumMarginPx: Int
    ): HandwritingActionRailSizing {
        if (availableHeightPx <= 0 || buttonCount <= 0) {
            return HandwritingActionRailSizing(preferredButtonSizePx, preferredMarginPx)
        }

        val preferred = HandwritingActionRailSizing(
            preferredButtonSizePx,
            preferredMarginPx
        )
        if (preferred.occupiedHeight(buttonCount) <= availableHeightPx) return preferred

        val minimumSpacedHeight = buttonCount * (minimumButtonSizePx + minimumMarginPx * 2)
        if (availableHeightPx >= minimumSpacedHeight) {
            val size = minOf(
                preferredButtonSizePx,
                (availableHeightPx - buttonCount * minimumMarginPx * 2) / buttonCount
            )
            val margin = minOf(
                preferredMarginPx,
                (availableHeightPx - size * buttonCount) / (buttonCount * 2)
            )
            return HandwritingActionRailSizing(size, margin)
        }

        // Very short floating or landscape panels must remain operable. Removing spacing before
        // shrinking below the preferred touch target keeps all actions reachable without overflow.
        return HandwritingActionRailSizing(
            buttonSizePx = minOf(minimumButtonSizePx, availableHeightPx / buttonCount),
            verticalMarginPx = 0
        )
    }
}

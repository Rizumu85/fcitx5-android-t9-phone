/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.view.View
import android.widget.TextView
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.data.theme.Theme

class T9ShortcutCandidateWidthMeasurer(
    ctx: Context,
    theme: Theme,
    setupTextView: TextView.() -> Unit,
    highlightCornerRadiusPx: Int
) {
    private val item = LabeledCandidateItemUi(ctx, theme, setupTextView, highlightCornerRadiusPx)
    private val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

    fun measure(candidate: FcitxEvent.Candidate, maxRootWidthPx: Int): Int {
        // Decision: T9 bubble width must use the same chip rules as the rendered row. Paint-only
        // estimates drift with custom fonts and long Latin words, which made English pages leave
        // arbitrary blank space after the last visible candidate.
        item.update(
            candidate = candidate,
            active = false,
            t9InputModeEnabled = true,
            shortcutLabel = "0",
            shortcutMaxWidthPx = maxRootWidthPx
        )
        item.root.measure(unspecified, unspecified)
        return item.root.measuredWidth.coerceAtLeast(1)
    }
}

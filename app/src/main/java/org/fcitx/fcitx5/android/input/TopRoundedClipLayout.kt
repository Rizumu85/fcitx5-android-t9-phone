/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.constraintlayout.widget.ConstraintLayout

class TopRoundedClipLayout(context: Context) : ConstraintLayout(context) {

    var topCornerRadiusPx: Int = 0
        set(value) {
            val coerced = value.coerceAtLeast(0)
            if (field == coerced) return
            field = coerced
            invalidateOutline()
        }

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = topCornerRadiusPx
                if (radius <= 0) {
                    outline.setRect(0, 0, view.width, view.height)
                    return
                }

                // Extending the rounded rect below the view keeps only the top corners visible.
                outline.setRoundRect(0, 0, view.width, view.height + radius, radius.toFloat())
            }
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.textView

class LabeledCandidateItemUi(
    override val ctx: Context,
    val theme: Theme,
    setupTextView: TextView.() -> Unit,
    private val highlightCornerRadiusPx: Int
) : Ui {

    private val useT9KeyboardLayout by AppPrefs.getInstance().keyboard.useT9KeyboardLayout

    override val root = textView {
        setupTextView(this)
    }

    fun update(candidate: FcitxEvent.Candidate, active: Boolean) {
        val labelFg = if (active) theme.genericActiveForegroundColor else theme.candidateLabelColor
        val fg = if (active) theme.genericActiveForegroundColor else theme.candidateTextColor
        val altFg = if (active) theme.genericActiveForegroundColor else theme.candidateCommentColor
        root.text = buildSpannedString {
            // Hide label and comment in T9 mode for cleaner display
            if (!useT9KeyboardLayout) {
                color(labelFg) { append(candidate.label) }
            }
            color(fg) { append(candidate.text) }
            if (!useT9KeyboardLayout && candidate.comment.isNotBlank()) {
                append(" ")
                color(altFg) { append(candidate.comment) }
            }
        }
        root.background = if (active) {
            GradientDrawable().apply {
                setColor(theme.genericActiveBackgroundColor)
                cornerRadius = highlightCornerRadiusPx.toFloat()
            }
        } else {
            null
        }
    }
}

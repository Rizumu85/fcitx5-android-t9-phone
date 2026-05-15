/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.SubscriptSpan
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.textView

class LabeledCandidateItemUi(
    override val ctx: Context,
    val theme: Theme,
    setupTextView: TextView.() -> Unit,
    private val highlightCornerRadiusPx: Int
) : Ui {

    private val activeBackground = GradientDrawable().apply {
        setColor(theme.genericActiveBackgroundColor)
        cornerRadius = highlightCornerRadiusPx.toFloat()
        alpha = 0
    }
    private var lastActive = false
    private var lastCandidateLabel = ""
    private var lastCandidateText = ""
    private var lastCandidateComment = ""

    override val root = textView {
        setupTextView(this)
        background = activeBackground
        isFocusable = false
        isFocusableInTouchMode = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
    }

    fun update(
        candidate: FcitxEvent.Candidate,
        active: Boolean,
        inactiveRow: Boolean = false,
        t9InputModeEnabled: Boolean = false,
        shortcutLabel: String? = null
    ) {
        val labelFg = when {
            active -> theme.genericActiveForegroundColor
            inactiveRow -> theme.candidateCommentColor
            else -> theme.candidateLabelColor
        }
        val fg = when {
            active -> theme.genericActiveForegroundColor
            inactiveRow -> theme.candidateCommentColor
            else -> theme.candidateTextColor
        }
        val altFg = if (active) theme.genericActiveForegroundColor else theme.candidateCommentColor
        root.gravity = if (t9InputModeEnabled && shortcutLabel != null) {
            Gravity.CENTER
        } else {
            Gravity.CENTER_VERTICAL
        }
        root.textAlignment = if (t9InputModeEnabled && shortcutLabel != null) {
            View.TEXT_ALIGNMENT_CENTER
        } else {
            View.TEXT_ALIGNMENT_GRAVITY
        }
        root.minWidth = if (t9InputModeEnabled && shortcutLabel != null) {
            (root.textSize * SHORTCUT_CANDIDATE_MIN_WIDTH_EM).toInt()
        } else {
            0
        }
        root.includeFontPadding = !(t9InputModeEnabled && shortcutLabel != null)
        root.setLineSpacing(0f, if (t9InputModeEnabled && shortcutLabel != null) 0.82f else 1f)
        root.text = buildSpannedString {
            // Hide label and comment in T9 mode for cleaner display
            if (!t9InputModeEnabled) {
                color(labelFg) { append(candidate.label) }
            }
            color(fg) { append(candidate.text) }
            if (t9InputModeEnabled && shortcutLabel != null) {
                append('\n')
                val start = length
                color(if (active) theme.genericActiveForegroundColor else theme.candidateCommentColor) {
                    append(shortcutLabel)
                }
                setSpan(
                    RelativeSizeSpan(SHORTCUT_LABEL_SCALE),
                    start,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    SubscriptSpan(),
                    start,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (!t9InputModeEnabled && candidate.comment.isNotBlank()) {
                append(" ")
                color(altFg) { append(candidate.comment) }
            }
        }
        if (candidate.label != lastCandidateLabel ||
            candidate.text != lastCandidateText ||
            candidate.comment != lastCandidateComment
        ) {
            lastCandidateLabel = candidate.label
            lastCandidateText = candidate.text
            lastCandidateComment = candidate.comment
            lastActive = false
            activeBackground.alpha = 0
            root.scaleX = 1f
            root.scaleY = 1f
            updateHighlight(active)
            return
        }
        updateHighlight(active)
    }

    private fun updateHighlight(active: Boolean) {
        val targetAlpha = if (active) 255 else 0
        val targetScale = if (active) ACTIVE_HIGHLIGHT_SCALE else 1f
        if (lastActive == active && activeBackground.alpha == targetAlpha) {
            root.scaleX = targetScale
            root.scaleY = targetScale
            return
        }
        lastActive = active
        activeBackground.alpha = targetAlpha
        root.scaleX = targetScale
        root.scaleY = targetScale
    }

    companion object {
        private const val ACTIVE_HIGHLIGHT_SCALE = 1.07f
        private const val SHORTCUT_LABEL_SCALE = 0.45f
        private const val SHORTCUT_CANDIDATE_MIN_WIDTH_EM = 1.35f
    }
}

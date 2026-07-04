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
    private var lastUsesShortcutLabel = false

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
        val usesShortcutLabel = t9InputModeEnabled && shortcutLabel != null
        lastUsesShortcutLabel = usesShortcutLabel
        root.includeFontPadding = !usesShortcutLabel
        root.setLineSpacing(0f, if (usesShortcutLabel) T9_SHORTCUT_LINE_SPACING else 1f)
        root.minHeight = 0
        updateSelection(
            candidate = candidate,
            active = active,
            inactiveRow = inactiveRow,
            t9InputModeEnabled = t9InputModeEnabled,
            shortcutLabel = shortcutLabel
        )
    }

    fun updateSelection(
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
        // Shortcut-number chips must stay inside TextView's measured box. Subscript spans and
        // vertical scaling can draw outside Flexbox/RecyclerView measurements, which clips T9
        // candidates in mixed Chinese/Rime-English pages.
        val targetScaleY = if (lastUsesShortcutLabel) 1f else targetScale
        if (lastActive == active && activeBackground.alpha == targetAlpha) {
            root.scaleX = targetScale
            root.scaleY = targetScaleY
            return
        }
        lastActive = active
        activeBackground.alpha = targetAlpha
        root.scaleX = targetScale
        root.scaleY = targetScaleY
    }

    companion object {
        private const val ACTIVE_HIGHLIGHT_SCALE = 1.07f
        private const val SHORTCUT_LABEL_SCALE = 0.45f
        private const val SHORTCUT_CANDIDATE_MIN_WIDTH_EM = 1.35f
        private const val T9_SHORTCUT_LINE_SPACING = 0.86f
    }
}

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
import android.text.TextUtils
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
import kotlin.math.roundToInt

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
    private var defaultMinHeight = 0

    override val root = textView {
        setupTextView(this)
        defaultMinHeight = minHeight
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
        shortcutLabel: String? = null,
        shortcutMaxWidthPx: Int? = null
    ) {
        val t9Shortcut = t9InputModeEnabled && shortcutLabel != null
        root.gravity = if (t9Shortcut) {
            Gravity.CENTER
        } else {
            Gravity.CENTER_VERTICAL
        }
        root.textAlignment = if (t9Shortcut) {
            View.TEXT_ALIGNMENT_CENTER
        } else {
            View.TEXT_ALIGNMENT_GRAVITY
        }
        root.minWidth = if (t9Shortcut) {
            (root.textSize * SHORTCUT_CANDIDATE_MIN_WIDTH_EM).toInt()
        } else {
            0
        }
        root.maxWidth = if (t9Shortcut) {
            shortcutMaxWidthPx ?: (ctx.resources.displayMetrics.widthPixels * SHORTCUT_CANDIDATE_MAX_WIDTH_SCREEN_RATIO).toInt()
        } else {
            Int.MAX_VALUE
        }
        root.ellipsize = if (t9Shortcut) TextUtils.TruncateAt.END else null
        root.includeFontPadding = true
        root.setLineSpacing(0f, if (t9Shortcut) SHORTCUT_LINE_SPACING_MULTIPLIER else 1f)
        if (t9Shortcut) {
            root.setMinLines(SHORTCUT_LINE_COUNT)
            root.maxLines = SHORTCUT_LINE_COUNT
            root.minHeight = shortcutCandidateMinHeight()
        } else {
            root.setMinLines(0)
            root.maxLines = Int.MAX_VALUE
            root.minHeight = defaultMinHeight
        }
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
        val renderedLabel = if (t9InputModeEnabled) "" else candidate.label
        val renderedComment = if (t9InputModeEnabled) "" else candidate.comment
        if (renderedLabel != lastCandidateLabel ||
            candidate.text != lastCandidateText ||
            renderedComment != lastCandidateComment
        ) {
            lastCandidateLabel = renderedLabel
            lastCandidateText = candidate.text
            lastCandidateComment = renderedComment
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

    private fun shortcutCandidateMinHeight(): Int {
        val textHeight = root.textSize * SHORTCUT_LINE_COUNT * SHORTCUT_LINE_SPACING_MULTIPLIER
        return (textHeight + root.paddingTop + root.paddingBottom).roundToInt()
            .coerceAtLeast(defaultMinHeight)
    }

    companion object {
        private const val ACTIVE_HIGHLIGHT_SCALE = 1.07f
        private const val SHORTCUT_LABEL_SCALE = 0.45f
        private const val SHORTCUT_CANDIDATE_MIN_WIDTH_EM = 0.95f
        private const val SHORTCUT_CANDIDATE_MAX_WIDTH_SCREEN_RATIO = 0.42f
        private const val SHORTCUT_LINE_COUNT = 2
        private const val SHORTCUT_LINE_SPACING_MULTIPLIER = 0.9f
    }
}

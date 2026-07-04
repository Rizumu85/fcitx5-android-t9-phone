/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
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

class T9ShortcutCandidateItemUi(
    override val ctx: Context,
    private val theme: Theme,
    setupTextView: TextView.() -> Unit,
    highlightCornerRadiusPx: Int
) : Ui {

    private val activeBackground = GradientDrawable().apply {
        setColor(theme.genericActiveBackgroundColor)
        cornerRadius = highlightCornerRadiusPx.toFloat()
        alpha = 0
    }
    private var lastActive = false
    private var lastCandidateText = ""
    private var lastShortcutLabel = ""
    private var lastInactiveRow = false
    private var lastShortcutMaxWidthPx = 0
    private var defaultMinHeight = 0

    override val root = textView {
        setupTextView(this)
        defaultMinHeight = minHeight
        background = activeBackground
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = true
        setLineSpacing(0f, SHORTCUT_LINE_SPACING_MULTIPLIER)
        setMinLines(SHORTCUT_LINE_COUNT)
        maxLines = SHORTCUT_LINE_COUNT
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
        shortcutLabel: String,
        shortcutMaxWidthPx: Int
    ) {
        root.minWidth = (root.textSize * SHORTCUT_CANDIDATE_MIN_WIDTH_EM).toInt()
        root.maxWidth = shortcutMaxWidthPx
        root.minHeight = shortcutCandidateMinHeight()
        val changed = candidate.text != lastCandidateText ||
            shortcutLabel != lastShortcutLabel ||
            inactiveRow != lastInactiveRow ||
            shortcutMaxWidthPx != lastShortcutMaxWidthPx
        if (changed) {
            lastCandidateText = candidate.text
            lastShortcutLabel = shortcutLabel
            lastInactiveRow = inactiveRow
            lastShortcutMaxWidthPx = shortcutMaxWidthPx
            lastActive = false
            activeBackground.alpha = 0
            root.scaleX = 1f
            root.scaleY = 1f
            renderText(active, inactiveRow)
            updateHighlight(active)
            return
        }
        updateSelection(active, inactiveRow)
    }

    fun updateSelection(
        active: Boolean,
        inactiveRow: Boolean = false
    ) {
        renderText(active, inactiveRow)
        updateHighlight(active)
    }

    private fun renderText(active: Boolean, inactiveRow: Boolean) {
        val fg = when {
            active -> theme.genericActiveForegroundColor
            inactiveRow -> theme.candidateCommentColor
            else -> theme.candidateTextColor
        }
        val shortcutFg = if (active) {
            theme.genericActiveForegroundColor
        } else {
            theme.candidateCommentColor
        }
        val availableTextWidth = (lastShortcutMaxWidthPx - root.paddingLeft - root.paddingRight)
            .coerceAtLeast(1)
            .toFloat()
        val firstLine = TextUtils.ellipsize(
            lastCandidateText,
            root.paint,
            availableTextWidth,
            TextUtils.TruncateAt.END
        )
        root.text = buildSpannedString {
            color(fg) { append(firstLine) }
            append('\n')
            val start = length
            color(shortcutFg) { append(lastShortcutLabel) }
            setSpan(
                RelativeSizeSpan(SHORTCUT_LABEL_SCALE),
                start,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
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
        private const val SHORTCUT_LINE_COUNT = 2
        private const val SHORTCUT_LINE_SPACING_MULTIPLIER = 0.96f
    }
}

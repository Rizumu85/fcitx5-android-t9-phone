/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.views.dsl.core.Ui

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

    private val candidateText = TextView(ctx).apply {
        setupTextView(this)
        isSingleLine = true
        includeFontPadding = false
        gravity = Gravity.CENTER
    }
    private val shortcutText = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, candidateText.textSize * SHORTCUT_LABEL_SCALE)
        typeface = candidateText.typeface
        isSingleLine = true
        includeFontPadding = false
        gravity = Gravity.CENTER
        visibility = View.GONE
    }

    private val itemPaddingLeft = candidateText.paddingLeft
    private val itemPaddingTop = candidateText.paddingTop
    private val itemPaddingRight = candidateText.paddingRight
    private val itemPaddingBottom = candidateText.paddingBottom

    override val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(itemPaddingLeft, itemPaddingTop, itemPaddingRight, itemPaddingBottom)
        background = activeBackground
        isFocusable = false
        isFocusableInTouchMode = false
        clipChildren = false
        clipToPadding = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        candidateText.setPadding(0, 0, 0, 0)
        addView(
            candidateText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        addView(
            shortcutText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    fun update(
        candidate: FcitxEvent.Candidate,
        active: Boolean,
        inactiveRow: Boolean = false,
        t9InputModeEnabled: Boolean = false,
        shortcutLabel: String? = null
    ) {
        val usesShortcutLabel = t9InputModeEnabled && shortcutLabel != null
        lastUsesShortcutLabel = usesShortcutLabel
        root.gravity = if (usesShortcutLabel) Gravity.CENTER else Gravity.CENTER_VERTICAL
        root.minimumWidth = if (usesShortcutLabel) {
            (candidateText.textSize * SHORTCUT_CANDIDATE_MIN_WIDTH_EM).toInt()
        } else {
            0
        }
        // T9 shortcut chips use real child views instead of newline spans. RecyclerView/Flexbox
        // sometimes reuses and measures candidates before the final draw pass; keeping text and
        // shortcut labels as separate measured children prevents page-flip-only clipping.
        shortcutText.visibility = if (usesShortcutLabel) View.VISIBLE else View.GONE
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
        if (t9InputModeEnabled && shortcutLabel != null) {
            candidateText.setTextColor(fg)
            candidateText.text = candidate.text
            shortcutText.setTextColor(if (active) theme.genericActiveForegroundColor else theme.candidateCommentColor)
            shortcutText.text = shortcutLabel
            shortcutText.visibility = View.VISIBLE
        } else {
            candidateText.text = buildSpannedString {
                color(labelFg) { append(candidate.label) }
                color(fg) { append(candidate.text) }
                if (candidate.comment.isNotBlank()) {
                    append(" ")
                    color(altFg) { append(candidate.comment) }
                }
            }
            shortcutText.text = ""
            shortcutText.visibility = View.GONE
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
        // T9 shortcut chips are two measured rows. Scaling only horizontally keeps the familiar
        // focus width without lying to the parent layout about vertical bounds.
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
    }
}

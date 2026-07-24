/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.t9.T9SemanticTextView
import splitties.views.dsl.core.Ui
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
    private var lastUsesShortcutLabel = false
    private var lastShortcutEdgeAlignedEnd = false

    private val candidateText = T9SemanticTextView(ctx).apply {
        setupTextView(this)
        isSingleLine = true
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
        shortcutLabel: String? = null,
        shortcutMaxWidthPx: Int? = null,
        shortcutEdgeAlignedStart: Boolean = false,
        shortcutEdgeAlignedEnd: Boolean = false
    ) {
        val usesShortcutLabel = t9InputModeEnabled && shortcutLabel != null
        lastUsesShortcutLabel = usesShortcutLabel
        lastShortcutEdgeAlignedEnd = usesShortcutLabel && shortcutEdgeAlignedEnd
        if (usesShortcutLabel && shortcutEdgeAlignedStart) {
            // The first focused chip grows into the row, not through the bubble's leading inset.
            // This preserves the accepted left margin without adding width that would disturb
            // measured paging or the pinyin row.
            root.pivotX = 0f
        }
        applyShortcutWidthLimit(if (usesShortcutLabel) shortcutMaxWidthPx else null)
        applyShortcutLineMetrics(usesShortcutLabel)
        root.gravity = if (usesShortcutLabel) Gravity.CENTER else Gravity.CENTER_VERTICAL
        root.minimumWidth = if (usesShortcutLabel) {
            if (lastShortcutEdgeAlignedEnd) {
                // Product decision: the final visible T9 shortcut chip is measured by its text.
                // Otherwise the minimum tap target becomes visible as inconsistent blank space
                // between the last word and the bubble edge.
                0
            } else {
                (candidateText.textSize * SHORTCUT_CANDIDATE_MIN_WIDTH_EM).toInt()
            }
        } else {
            0
        }
        candidateText.includeFontPadding = true
        // T9 shortcut chips use real child views instead of newline spans. RecyclerView/Flexbox
        // sometimes reuses and measures candidates before the final draw pass; keeping text and
        // shortcut labels as separate measured children prevents page-flip-only clipping. The
        // main candidate line keeps font padding because the bundled UI font can draw tall Latin
        // glyphs above TextView's tight bounds, which was visible in Rime's English candidates.
        shortcutText.visibility = if (usesShortcutLabel) View.VISIBLE else View.GONE
        updateSelection(
            candidate = candidate,
            active = active,
            inactiveRow = inactiveRow,
            t9InputModeEnabled = t9InputModeEnabled,
            shortcutLabel = shortcutLabel,
            shortcutMaxWidthPx = shortcutMaxWidthPx
        )
    }

    fun updateSelection(
        candidate: FcitxEvent.Candidate,
        active: Boolean,
        inactiveRow: Boolean = false,
        t9InputModeEnabled: Boolean = false,
        shortcutLabel: String? = null,
        shortcutMaxWidthPx: Int? = null
    ) {
        applyShortcutWidthLimit(if (t9InputModeEnabled && shortcutLabel != null) shortcutMaxWidthPx else null)
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

    private fun applyShortcutWidthLimit(maxRootWidthPx: Int?) {
        if (maxRootWidthPx == null || maxRootWidthPx <= 0) {
            candidateText.maxWidth = Int.MAX_VALUE
            candidateText.ellipsize = null
            return
        }
        val textMaxWidth = (maxRootWidthPx - root.paddingLeft - root.paddingRight)
            .coerceAtLeast(candidateText.textSize.toInt())
        candidateText.maxWidth = textMaxWidth
        candidateText.ellipsize = TextUtils.TruncateAt.END
    }

    private fun applyShortcutLineMetrics(usesShortcutLabel: Boolean) {
        val candidateHeight = if (usesShortcutLabel) {
            (candidateText.textSize * SHORTCUT_CANDIDATE_LINE_HEIGHT_EM).roundToInt()
        } else {
            LinearLayout.LayoutParams.WRAP_CONTENT
        }
        val shortcutHeight = if (usesShortcutLabel) {
            (candidateText.textSize * SHORTCUT_LABEL_LINE_HEIGHT_EM).roundToInt()
        } else {
            LinearLayout.LayoutParams.WRAP_CONTENT
        }
        // Product decision: custom UI fonts may fall back per glyph, changing TextView metrics.
        // T9 number labels must stay on one visual baseline, so the two shortcut rows use fixed
        // line boxes while the parent still allows tall fallback glyphs to draw outside.
        updateChildHeight(candidateText, candidateHeight)
        updateChildHeight(shortcutText, shortcutHeight)
    }

    private fun updateChildHeight(view: TextView, height: Int) {
        val params = view.layoutParams as? LinearLayout.LayoutParams ?: return
        if (params.height == height) return
        params.height = height
        view.layoutParams = params
    }

    companion object {
        private const val ACTIVE_HIGHLIGHT_SCALE = 1.07f
        private const val SHORTCUT_LABEL_SCALE = 0.45f
        private const val SHORTCUT_CANDIDATE_MIN_WIDTH_EM = 1.35f
        private const val SHORTCUT_CANDIDATE_LINE_HEIGHT_EM = 1.45f
        private const val SHORTCUT_LABEL_LINE_HEIGHT_EM = 0.62f
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.InputUiFont
import org.fcitx.fcitx5.android.utils.borderlessRippleDrawable
import splitties.dimensions.dp
import kotlin.math.max

class HandwritingCandidateStrip(
    context: Context,
    private val theme: Theme,
    candidateTextSizeSp: Float,
    private val onCandidateClick: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) : LinearLayout(context) {
    private val candidateRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(
            candidateRow,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        )
    }
    private val previousButton = pageButton(
        icon = R.drawable.ic_baseline_keyboard_arrow_left_24,
        description = R.string.prev,
        action = onPreviousPage
    )
    private val nextButton = pageButton(
        icon = R.drawable.ic_baseline_keyboard_arrow_right_24,
        description = R.string.next,
        action = onNextPage
    )
    private val cells = Array(MaxCandidateCount) {
        CandidateCell(context, theme, candidateTextSizeSp, onCandidateClick).also { cell ->
            candidateRow.addView(
                cell,
                LayoutParams(context.dp(CandidateWidthDp), LayoutParams.MATCH_PARENT)
            )
        }
    }
    private var renderedPage = HandwritingCandidatePage.Empty

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        addView(previousButton, LayoutParams(context.dp(PageButtonWidthDp), LayoutParams.MATCH_PARENT))
        addView(scroller, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(nextButton, LayoutParams(context.dp(PageButtonWidthDp), LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    fun render(page: HandwritingCandidatePage) {
        renderedPage = page
        visibility = if (page.items.isEmpty()) GONE else VISIBLE
        val paged = page.pageCount > 1
        previousButton.visibility = when {
            !paged -> GONE
            page.hasPreviousPage -> VISIBLE
            else -> INVISIBLE
        }
        nextButton.visibility = when {
            !paged -> GONE
            page.hasNextPage -> VISIBLE
            else -> INVISIBLE
        }
        cells.forEachIndexed { index, cell ->
            val item = page.items.getOrNull(index)
            cell.visibility = if (item == null) GONE else VISIBLE
            if (item != null) {
                cell.bind(item, item.originalIndex == page.selectedOriginalIndex)
            }
        }
        if (page.items.isNotEmpty()) post(::revealSelection)
    }

    private fun revealSelection() {
        if (distributeVisibleCells()) {
            post(::revealSelection)
            return
        }
        val selected = cells.firstOrNull { cell ->
            cell.visibility == VISIBLE && cell.originalIndex == renderedPage.selectedOriginalIndex
        } ?: return
        val safeMargin = context.dp(4)
        val visibleLeft = scroller.scrollX + safeMargin
        val visibleRight = scroller.scrollX + scroller.width - safeMargin
        when {
            selected.left < visibleLeft -> scroller.scrollTo((selected.left - safeMargin).coerceAtLeast(0), 0)
            selected.right > visibleRight -> scroller.scrollTo(
                (selected.right - scroller.width + safeMargin).coerceAtLeast(0),
                0
            )
        }
    }

    private fun distributeVisibleCells(): Boolean {
        val count = renderedPage.items.size
        if (count == 0 || scroller.width <= 0) return false
        // Sparse handwriting results should read as one balanced strip like the reference layout;
        // dense pages retain a minimum cell width and become horizontally scrollable on narrow UI.
        val width = max(context.dp(CandidateWidthDp), scroller.width / count)
        var changed = false
        cells.forEachIndexed { index, cell ->
            if (index >= count) return@forEachIndexed
            val params = cell.layoutParams as LayoutParams
            if (params.width != width) {
                params.width = width
                cell.layoutParams = params
                changed = true
            }
        }
        return changed
    }

    private fun pageButton(icon: Int, description: Int, action: () -> Unit) =
        ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(theme.altKeyTextColor)
            contentDescription = context.getString(description)
            setPadding(context.dp(2), context.dp(8), context.dp(2), context.dp(8))
            background = borderlessRippleDrawable(theme.keyPressHighlightColor, context.dp(14))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { action() }
        }

    private class CandidateCell(
        context: Context,
        private val theme: Theme,
        candidateTextSizeSp: Float,
        onCandidateClick: (Int) -> Unit
    ) : LinearLayout(context) {
        private val candidateText = TextView(context).apply {
            textSize = candidateTextSizeSp
            includeFontPadding = false
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            InputUiFont.applyTo(this, Typeface.BOLD)
        }
        private val shortcutText = TextView(context).apply {
            textSize = ShortcutTextSizeSp
            includeFontPadding = false
            isSingleLine = true
            gravity = Gravity.CENTER
            InputUiFont.applyTo(this)
        }
        var originalIndex = -1
            private set

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            background = borderlessRippleDrawable(theme.keyPressHighlightColor, context.dp(10))
            addView(candidateText, LayoutParams(LayoutParams.MATCH_PARENT, 0, CandidateWeight))
            addView(shortcutText, LayoutParams(LayoutParams.MATCH_PARENT, 0, ShortcutWeight))
            setOnClickListener {
                originalIndex.takeIf { index -> index >= 0 }?.let(onCandidateClick)
            }
        }

        fun bind(item: HandwritingCandidateItem, selected: Boolean) {
            originalIndex = item.originalIndex
            candidateText.text = item.text
            shortcutText.text = item.shortcutLabel
            candidateText.setTextColor(
                if (selected) theme.genericActiveBackgroundColor else theme.altKeyTextColor
            )
            shortcutText.setTextColor(
                if (selected) theme.genericActiveBackgroundColor else theme.candidateCommentColor
            )
        }
    }

    private companion object {
        const val MaxCandidateCount = 10
        const val CandidateWidthDp = 34
        const val PageButtonWidthDp = 24
        const val ShortcutTextSizeSp = 8f
        const val CandidateWeight = 2.5f
        const val ShortcutWeight = 1f
    }
}

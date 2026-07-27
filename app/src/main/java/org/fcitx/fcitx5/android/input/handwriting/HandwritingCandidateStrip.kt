/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.InputUiFont
import org.fcitx.fcitx5.android.utils.borderlessRippleDrawable
import splitties.dimensions.dp
import kotlin.math.ceil
import kotlin.math.max

class HandwritingCandidateStrip(
    context: Context,
    private val theme: Theme,
    candidateTextSizeSp: Float,
    private val onCandidateClick: (Int) -> Unit
) : LinearLayout(context) {
    private val candidateRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            0,
            context.dp(CandidateVerticalInsetDp),
            0,
            context.dp(CandidateVerticalInsetDp)
        )
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
    private val cells = Array(MaxCandidateCount) {
        CandidateCell(context, theme, candidateTextSizeSp, onCandidateClick).also { cell ->
            candidateRow.addView(
                cell,
                LayoutParams(context.dp(ChineseCandidateWidthDp), LayoutParams.MATCH_PARENT)
            )
        }
    }
    private val widthPolicy = HandwritingCandidateWidthPolicy(
        compactChineseWidthPx = context.dp(ChineseCandidateWidthDp),
        naturalMinimumWidthPx = context.dp(NaturalCandidateMinimumWidthDp),
        naturalHorizontalInsetPx = context.dp(NaturalCandidateHorizontalInsetDp),
        viewportInsetPx = context.dp(CandidateViewportInsetDp)
    )
    private var renderedPage = HandwritingCandidatePage.Empty
    private var renderedLanguage = HandwritingLanguage.CHINESE

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        addView(scroller, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        visibility = GONE
    }

    fun render(page: HandwritingCandidatePage, language: HandwritingLanguage) {
        renderedPage = page
        renderedLanguage = language
        visibility = if (page.items.isEmpty()) GONE else VISIBLE
        cells.forEachIndexed { index, cell ->
            val item = page.items.getOrNull(index)
            cell.visibility = if (item == null) GONE else VISIBLE
            if (item != null) {
                cell.bind(item, item.originalIndex == page.selectedOriginalIndex)
            }
        }
        if (page.items.isNotEmpty()) post(::revealSelection)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Resolve phrase geometry during measure so the first visible prediction frame is already
        // final; correcting compact placeholder widths after draw would flash "character + ...".
        layoutVisibleCells(MeasureSpec.getSize(widthMeasureSpec))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun revealSelection() {
        if (layoutVisibleCells(scroller.width)) {
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

    private fun layoutVisibleCells(viewportWidth: Int): Boolean {
        val count = renderedPage.items.size
        if (count == 0 || viewportWidth <= 0) return false
        val widths = widthPolicy.resolve(
            language = renderedLanguage,
            candidateTexts = renderedPage.items.map(HandwritingCandidateItem::text),
            measuredContentWidthsPx = cells.take(count).map(CandidateCell::measuredContentWidth),
            viewportWidthPx = viewportWidth
        )
        var changed = false
        cells.forEachIndexed { index, cell ->
            if (index >= count) return@forEachIndexed
            val width = widths[index]
            val params = cell.layoutParams as LayoutParams
            if (params.width != width) {
                params.width = width
                cell.layoutParams = params
                changed = true
            }
        }
        return changed
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

        fun measuredContentWidth(): Int =
            ceil(
                max(
                    candidateText.paint.measureText(candidateText.text.toString()),
                    shortcutText.paint.measureText(shortcutText.text.toString())
                )
            )
                .toInt()
    }

    private companion object {
        const val MaxCandidateCount = 10
        const val ChineseCandidateWidthDp = 34
        const val NaturalCandidateMinimumWidthDp = 48
        const val NaturalCandidateHorizontalInsetDp = 10
        const val CandidateViewportInsetDp = 8
        const val CandidateVerticalInsetDp = 2
        const val ShortcutTextSizeSp = 8f
        const val CandidateWeight = 2.5f
        const val ShortcutWeight = 1f
    }
}

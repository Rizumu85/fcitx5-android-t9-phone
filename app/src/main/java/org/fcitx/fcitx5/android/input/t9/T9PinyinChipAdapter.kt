/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.t9

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.InputUiFont
import kotlin.math.ceil
import kotlin.math.roundToInt

class T9PinyinChipAdapter(
    context: Context,
    private val theme: Theme,
    textSizeSp: Float,
    private val horizontalPaddingPx: Int,
    private val verticalPaddingPx: Int,
    private val rowHeightPx: Int,
    cornerRadiusPx: Float,
    precreatedChipCount: Int = 0,
    private val onChipClick: (String) -> Unit
) {

    private var pinyins: List<String> = emptyList()
    private var highlightActive = false

    private val stripView = PinyinChipStripView(
        context = context,
        theme = theme,
        textSizeSp = textSizeSp,
        horizontalPaddingPx = horizontalPaddingPx,
        verticalPaddingPx = verticalPaddingPx,
        rowHeightPx = rowHeightPx,
        cornerRadiusPx = cornerRadiusPx,
        onChipClick = onChipClick
    )

    val root: HorizontalScrollView = HorizontalScrollView(context).apply {
        overScrollMode = View.OVER_SCROLL_NEVER
        isHorizontalScrollBarEnabled = false
        clipChildren = false
        clipToPadding = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        isFocusable = false
        isFocusableInTouchMode = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        setBackgroundColor(Color.TRANSPARENT)
        addView(stripView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            rowHeightPx
        ))
    }

    init {
        stripView.reserveChipCapacity(precreatedChipCount)
    }

    var highlightedIndex: Int = 0
        private set

    fun submitList(newCandidates: List<String>, newHighlightedIndex: Int = highlightedIndex): Boolean {
        val nextPinyins = newCandidates.toList()
        val nextHighlightedIndex = newHighlightedIndex.coerceIn(0, nextPinyins.lastIndex.coerceAtLeast(0))
        val changed = pinyins != nextPinyins || highlightedIndex != nextHighlightedIndex
        pinyins = nextPinyins
        highlightedIndex = nextHighlightedIndex
        if (changed) {
            stripView.submit(
                items = pinyins,
                highlightedIndex = highlightedIndex,
                highlightActive = highlightActive
            )
        }
        return changed
    }

    fun clear() {
        submitList(emptyList())
    }

    fun getHighlightedPinyin(): String? = pinyins.getOrNull(highlightedIndex)

    val itemCount: Int
        get() = pinyins.size

    fun laidOutContentWidthPx(): Int? =
        stripView.contentWidthPx.takeIf { it > 0 }

    fun setHighlightActive(active: Boolean) {
        if (highlightActive == active) return
        highlightActive = active
        stripView.submit(
            items = pinyins,
            highlightedIndex = highlightedIndex,
            highlightActive = highlightActive
        )
    }

    fun moveHighlightedIndex(delta: Int): Boolean {
        if (pinyins.isEmpty()) return false
        val newIndex = (highlightedIndex + delta).coerceIn(0, pinyins.lastIndex)
        if (newIndex == highlightedIndex) return false
        highlightedIndex = newIndex
        stripView.submit(
            items = pinyins,
            highlightedIndex = highlightedIndex,
            highlightActive = highlightActive
        )
        return true
    }

    fun scrollToStart() {
        if (root.scrollX != 0) {
            root.scrollTo(0, 0)
        }
    }

    fun scrollToHighlighted() {
        scrollToHighlighted(null)
    }

    fun scrollToHighlighted(viewportWidthPx: Int?) {
        root.post {
            scrollHighlightedIntoView(viewportWidthPx)
        }
    }

    private fun scrollHighlightedIntoView(viewportWidthPx: Int?) {
        val viewportWidth = (viewportWidthPx?.takeIf { it > 0 }
            ?: root.width.takeIf { it > 0 }
            ?: return) - root.paddingLeft - root.paddingRight
        if (viewportWidth <= 0) return
        val bounds = stripView.itemBounds(highlightedIndex) ?: return
        val targetScrollX = T9PinyinChipScrollPlanner.targetScrollX(
            currentScrollX = root.scrollX,
            viewportWidthPx = viewportWidth,
            itemStartPx = bounds.startPx,
            itemEndPx = bounds.endPx
        )
        if (targetScrollX != root.scrollX) {
            root.scrollTo(targetScrollX, 0)
        }
    }

    private class PinyinChipStripView(
        context: Context,
        private val theme: Theme,
        textSizeSp: Float,
        private val horizontalPaddingPx: Int,
        private val verticalPaddingPx: Int,
        private val rowHeightPx: Int,
        private val cornerRadiusPx: Float,
        private val onChipClick: (String) -> Unit
    ) : View(context) {
        data class ItemBounds(val startPx: Int, val endPx: Int)

        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizeSp * context.resources.displayMetrics.scaledDensity
            InputUiFont.applyTo(this)
        }
        private val activeBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.genericActiveBackgroundColor
        }
        private val activeRect = RectF()

        private var items: List<String> = emptyList()
        private var highlightedIndex: Int = 0
        private var highlightActive: Boolean = false
        private var touchDownX: Float? = null
        private val itemBounds = mutableListOf<ItemBounds>()

        var contentWidthPx: Int = 0
            private set

        init {
            gravityCompat()
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = false
            isFocusableInTouchMode = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                defaultFocusHighlightEnabled = false
            }
        }

        fun reserveChipCapacity(count: Int) {
            if (count > itemBounds.size) {
                requestLayout()
            }
        }

        fun submit(items: List<String>, highlightedIndex: Int, highlightActive: Boolean) {
            this.items = items
            this.highlightedIndex = highlightedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
            this.highlightActive = highlightActive
            recalculateBounds()
            requestLayout()
            invalidate()
        }

        fun itemBounds(index: Int): ItemBounds? =
            itemBounds.getOrNull(index)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (itemBounds.size != items.size) {
                recalculateBounds()
            }
            val width = contentWidthPx.coerceAtLeast(1)
            setMeasuredDimension(
                resolveSize(width, widthMeasureSpec),
                resolveSize(rowHeightPx, heightMeasureSpec)
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val baseline = textBaseline()
            items.forEachIndexed { index, item ->
                val bounds = itemBounds.getOrNull(index) ?: return@forEachIndexed
                val active = highlightActive && index == highlightedIndex
                if (active) {
                    activeRect.set(
                        bounds.startPx.toFloat(),
                        verticalPaddingPx.toFloat(),
                        bounds.endPx.toFloat() - if (index != items.lastIndex) horizontalPaddingPx else 0,
                        (height - verticalPaddingPx).toFloat()
                    )
                    canvas.drawRoundRect(activeRect, cornerRadiusPx, cornerRadiusPx, activeBackgroundPaint)
                }
                textPaint.color = when {
                    active -> theme.genericActiveForegroundColor
                    !highlightActive -> theme.candidateCommentColor
                    else -> theme.candidateTextColor
                }
                val textX = bounds.startPx + horizontalPaddingPx.toFloat()
                if (active) {
                    val centerX = (bounds.startPx + bounds.endPx) / 2f
                    val centerY = height / 2f
                    canvas.save()
                    canvas.scale(ACTIVE_HIGHLIGHT_SCALE, ACTIVE_HIGHLIGHT_SCALE, centerX, centerY)
                    canvas.drawText(item, textX, baseline, textPaint)
                    canvas.restore()
                } else {
                    canvas.drawText(item, textX, baseline, textPaint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val downX = touchDownX
                    touchDownX = null
                    if (downX != null && kotlin.math.abs(event.x - downX) <= touchSlopPx()) {
                        val index = itemBounds.indexOfFirst { event.x >= it.startPx && event.x <= it.endPx }
                        items.getOrNull(index)?.let(onChipClick)
                    }
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    touchDownX = null
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun recalculateBounds() {
            itemBounds.clear()
            var x = 0
            items.forEachIndexed { index, item ->
                val textWidth = ceil(textPaint.measureText(item).toDouble()).roundToInt()
                val chipWidth = textWidth + horizontalPaddingPx * 2
                val margin = if (index != items.lastIndex) horizontalPaddingPx else 0
                itemBounds += ItemBounds(startPx = x, endPx = x + chipWidth + margin)
                x += chipWidth + margin
            }
            contentWidthPx = x
        }

        private fun textBaseline(): Float {
            val metrics = textPaint.fontMetrics
            return height / 2f - (metrics.ascent + metrics.descent) / 2f
        }

        private fun touchSlopPx(): Float =
            (context.resources.displayMetrics.density * 8f).coerceAtLeast(4f)

        private fun gravityCompat() {
            textAlignment = TEXT_ALIGNMENT_GRAVITY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                textDirection = TEXT_DIRECTION_LTR
            }
        }

        companion object {
            private const val ACTIVE_HIGHLIGHT_SCALE = 1.06f
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.t9

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.InputUiFont

class T9PinyinChipAdapter(
    context: Context,
    private val theme: Theme,
    private val textSizeSp: Float,
    private val horizontalPaddingPx: Int,
    private val verticalPaddingPx: Int,
    private val rowHeightPx: Int,
    private val cornerRadiusPx: Float,
    private val precreatedChipCount: Int = 0,
    private val onChipClick: (String) -> Unit
) {

    private var pinyins: List<String> = emptyList()
    private var highlightActive = false
    private val chips = mutableListOf<TextView>()
    private val chipBackgrounds = mutableListOf<GradientDrawable>()

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        clipChildren = false
        clipToPadding = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        isFocusable = false
        isFocusableInTouchMode = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        setBackgroundColor(Color.TRANSPARENT)
    }

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
        addView(container, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            rowHeightPx
        ))
    }

    init {
        ensureChipCapacity(precreatedChipCount)
    }

    var highlightedIndex: Int = 0
        private set

    fun submitList(newCandidates: List<String>, newHighlightedIndex: Int = highlightedIndex): Boolean {
        val oldPinyins = pinyins
        val oldHighlightedIndex = highlightedIndex
        val nextPinyins = newCandidates.toList()
        val nextHighlightedIndex = newHighlightedIndex.coerceIn(0, (nextPinyins.lastIndex).coerceAtLeast(0))
        val plan = T9PinyinChipUpdatePlanner.plan(
            oldItems = oldPinyins,
            newItems = nextPinyins,
            oldHighlightedIndex = oldHighlightedIndex,
            newHighlightedIndex = nextHighlightedIndex
        )
        pinyins = nextPinyins
        highlightedIndex = nextHighlightedIndex
        if (plan.changed || plan.styleIndices.isNotEmpty()) {
            updateChips(plan)
        }
        return plan.changed
    }

    fun clear() {
        submitList(emptyList())
    }

    fun getHighlightedPinyin(): String? = pinyins.getOrNull(highlightedIndex)

    fun setHighlightActive(active: Boolean) {
        if (highlightActive == active) return
        highlightActive = active
        updateAllChips()
    }

    fun moveHighlightedIndex(delta: Int): Boolean {
        if (pinyins.isEmpty()) return false
        val oldIndex = highlightedIndex
        val newIndex = (highlightedIndex + delta).coerceIn(0, pinyins.lastIndex)
        if (newIndex == highlightedIndex) return false
        highlightedIndex = newIndex
        updateChipAt(oldIndex)
        updateChipAt(newIndex)
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
        val chip = chips.getOrNull(highlightedIndex) ?: return
        val viewportWidth = viewportWidthPx?.takeIf { it > 0 }
            ?: root.width.takeIf { it > 0 }
            ?: return
        val params = chip.layoutParams as? LinearLayout.LayoutParams
        val chipStart = chip.left
        val chipEnd = chip.right + (params?.rightMargin ?: 0)
        val visibleStart = root.scrollX
        val visibleEnd = visibleStart + viewportWidth
        val targetScrollX = when {
            chipStart < visibleStart -> chipStart
            chipEnd > visibleEnd -> chipEnd - viewportWidth
            else -> visibleStart
        }.coerceAtLeast(0)
        if (targetScrollX != root.scrollX) {
            root.scrollTo(targetScrollX, 0)
        }
    }

    private fun updateChips(plan: T9PinyinChipUpdatePlanner.Plan) {
        syncChipCount(pinyins.size)
        plan.bindIndices.forEach { index ->
            pinyins.getOrNull(index)?.let { bindChip(index, it) }
        }
        plan.styleIndices.forEach(::updateChipAt)
    }

    private fun syncChipCount(targetSize: Int) {
        ensureChipCapacity(targetSize)
        chips.forEachIndexed { index, chip ->
            val targetVisibility = if (index < targetSize) View.VISIBLE else View.GONE
            if (chip.visibility != targetVisibility) {
                chip.visibility = targetVisibility
            }
        }
    }

    private fun ensureChipCapacity(targetSize: Int) {
        while (chips.size < targetSize) {
            val chip = createChip()
            val background = createChipBackground()
            chip.background = background
            chip.visibility = View.GONE
            chips += chip
            chipBackgrounds += background
            container.addView(chip, chipLayoutParams(chips.lastIndex))
        }
    }

    private fun createChip(): TextView =
        TextView(root.context).apply {
            setTextColor(theme.candidateTextColor)
            textSize = textSizeSp
            InputUiFont.applyTo(this)
            setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
            minHeight = rowHeightPx
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            includeFontPadding = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setLineHeight(rowHeightPx)
            }
            isFocusable = false
            isFocusableInTouchMode = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                defaultFocusHighlightEnabled = false
            }
            setOnClickListener {
                (tag as? String)?.let(onChipClick)
            }
        }

    private fun createChipBackground(): GradientDrawable =
        GradientDrawable().apply {
            setColor(theme.genericActiveBackgroundColor)
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            alpha = 0
        }

    private fun bindChip(index: Int, pinyin: String) {
        val chip = chips[index]
        if (chip.text.toString() != pinyin) {
            chip.text = pinyin
        }
        chip.tag = pinyin
        val params = chip.layoutParams as? LinearLayout.LayoutParams
        val expectedRightMargin = if (index != pinyins.lastIndex) horizontalPaddingPx else 0
        if (params?.rightMargin != expectedRightMargin) {
            chip.layoutParams = chipLayoutParams(index)
        }
    }

    private fun chipLayoutParams(index: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            if (index != pinyins.lastIndex) {
                rightMargin = horizontalPaddingPx
            }
        }

    private fun updateAllChips() {
        chips.forEachIndexed { index, chip ->
            updateChip(chip, chipBackgrounds[index], active = highlightActive && index == highlightedIndex)
        }
    }

    private fun updateChipAt(index: Int) {
        val chip = chips.getOrNull(index) ?: return
        val background = chipBackgrounds.getOrNull(index) ?: return
        updateChip(chip, background, active = highlightActive && index == highlightedIndex)
    }

    private fun updateChip(chip: TextView, background: GradientDrawable, active: Boolean) {
        val inactiveRow = !highlightActive
        chip.setTextColor(when {
            active -> theme.genericActiveForegroundColor
            inactiveRow -> theme.candidateCommentColor
            else -> theme.candidateTextColor
        })
        background.alpha = if (active) 255 else 0
        val scale = if (active) ACTIVE_HIGHLIGHT_SCALE else 1f
        chip.scaleX = scale
        chip.scaleY = scale
    }

    companion object {
        private const val ACTIVE_HIGHLIGHT_SCALE = 1.06f
    }
}

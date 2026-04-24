/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.t9

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.data.theme.Theme

class T9PinyinChipAdapter(
    private val theme: Theme,
    private val textSizeSp: Float,
    private val horizontalPaddingPx: Int,
    private val verticalPaddingPx: Int,
    private val rowHeightPx: Int,
    private val cornerRadiusPx: Float,
    private val onChipClick: (String) -> Unit
) : RecyclerView.Adapter<T9PinyinChipAdapter.ViewHolder>() {

    private var pinyins: List<String> = emptyList()
    private var highlightActive = false

    var highlightedIndex: Int = 0
        private set

    init {
        setHasStableIds(true)
    }

    fun submitList(newCandidates: List<String>): Boolean {
        val changed = pinyins != newCandidates
        pinyins = newCandidates
        highlightedIndex = highlightedIndex.coerceIn(0, (pinyins.lastIndex).coerceAtLeast(0))
        notifyDataSetChanged()
        return changed
    }

    fun clear() {
        submitList(emptyList())
    }

    fun getHighlightedPinyin(): String? = pinyins.getOrNull(highlightedIndex)

    fun setHighlightActive(active: Boolean) {
        if (highlightActive == active) return
        highlightActive = active
        notifyDataSetChanged()
    }

    fun moveHighlightedIndex(delta: Int): Boolean {
        if (pinyins.isEmpty()) return false
        val newIndex = (highlightedIndex + delta).coerceIn(0, pinyins.lastIndex)
        if (newIndex == highlightedIndex) return false
        highlightedIndex = newIndex
        notifyDataSetChanged()
        return true
    }

    override fun getItemCount(): Int = pinyins.size

    override fun getItemId(position: Int): Long = pinyins[position].hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val chip = TextView(parent.context).apply {
            setTextColor(theme.candidateTextColor)
            textSize = textSizeSp
            setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
            minHeight = rowHeightPx
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            includeFontPadding = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setLineHeight(rowHeightPx)
            }
        }
        chip.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return ViewHolder(chip)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pinyin = pinyins[position]
        holder.update(pinyin, active = highlightActive && position == highlightedIndex)
        holder.chip.setOnClickListener { onChipClick(pinyin) }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.chip.setOnClickListener(null)
        holder.cancelAnimations()
        super.onViewRecycled(holder)
    }

    inner class ViewHolder(val chip: TextView) : RecyclerView.ViewHolder(chip) {
        private val activeBackground = GradientDrawable().apply {
            setColor(theme.genericActiveBackgroundColor)
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            alpha = 0
        }
        private var lastActive = false
        private var lastSignature = ""
        private var highlightAnimator: ValueAnimator? = null

        init {
            chip.background = activeBackground
        }

        fun update(pinyin: String, active: Boolean) {
            chip.text = pinyin
            chip.setTextColor(
                if (active) theme.genericActiveForegroundColor else theme.candidateTextColor
            )
            if (pinyin != lastSignature) {
                lastSignature = pinyin
                highlightAnimator?.cancel()
                lastActive = active
                activeBackground.alpha = if (active) 255 else 0
                val scale = if (active) ACTIVE_HIGHLIGHT_SCALE else 1f
                chip.scaleX = scale
                chip.scaleY = scale
                return
            }
            animateHighlight(active)
        }

        fun cancelAnimations() {
            highlightAnimator?.cancel()
        }

        private fun animateHighlight(active: Boolean) {
            val targetAlpha = if (active) 255 else 0
            val targetScale = if (active) ACTIVE_HIGHLIGHT_SCALE else 1f
            if (lastActive == active && activeBackground.alpha == targetAlpha) {
                chip.scaleX = targetScale
                chip.scaleY = targetScale
                return
            }
            lastActive = active
            highlightAnimator?.cancel()
            val startAlpha = activeBackground.alpha
            val startScale = chip.scaleX.takeIf { it > 0f } ?: 1f
            highlightAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = HIGHLIGHT_ANIMATION_DURATION_MS
                interpolator =
                    if (active) HIGHLIGHT_ACTIVATE_INTERPOLATOR else AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    activeBackground.alpha =
                        (startAlpha + (targetAlpha - startAlpha) * progress).toInt()
                    val scale = startScale + (targetScale - startScale) * progress
                    chip.scaleX = scale
                    chip.scaleY = scale
                }
                start()
            }
        }
    }

    companion object {
        private const val HIGHLIGHT_ANIMATION_DURATION_MS = 180L
        private const val ACTIVE_HIGHLIGHT_SCALE = 1.06f
        private val HIGHLIGHT_ACTIVATE_INTERPOLATOR = OvershootInterpolator(0.5f)
    }
}

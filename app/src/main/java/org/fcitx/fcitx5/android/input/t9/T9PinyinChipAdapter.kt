/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.t9

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
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
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.TRANSPARENT)
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusPx
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
        holder.chip.text = pinyin
        holder.chip.setOnClickListener { onChipClick(pinyin) }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.chip.setOnClickListener(null)
        super.onViewRecycled(holder)
    }

    class ViewHolder(val chip: TextView) : RecyclerView.ViewHolder(chip)
}

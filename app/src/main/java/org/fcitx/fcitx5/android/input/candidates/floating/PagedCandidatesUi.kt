/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FcitxEvent.PagedCandidateEvent.LayoutHint
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import kotlin.math.roundToInt
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.recyclerview.recyclerView

class PagedCandidatesUi(
    override val ctx: Context,
    val theme: Theme,
    private val setupTextView: TextView.() -> Unit,
    private val showPaginationArrows: Boolean,
    private val highlightCornerRadiusPx: Int,
    private val onCandidateClick: (Int) -> Unit,
    private val onPrevPage: () -> Unit,
    private val onNextPage: () -> Unit
) : Ui {

    private var data = FcitxEvent.PagedCandidateEvent.Data.Empty

    private var isVertical = false
    private var highlightActive = true
    private var showShortcutLabels = false
    private val t9InputModeEnabledPref = AppPrefs.getInstance().keyboard.useT9KeyboardLayout

    @Volatile
    private var t9InputModeEnabled = t9InputModeEnabledPref.getValue()

    private val t9InputModeEnabledChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            t9InputModeEnabled = value
        }

    init {
        t9InputModeEnabledPref.registerOnChangeListener(t9InputModeEnabledChangeListener)
    }

    private val highlightOverflowPaddingPx =
        (highlightCornerRadiusPx * 0.35f).roundToInt().coerceAtLeast(ctx.dp(2))

    private sealed class Row {
        data class Candidate(val candidate: FcitxEvent.Candidate) : Row()
        data class Pagination(val hasPrev: Boolean, val hasNext: Boolean) : Row()
    }

    sealed class UiHolder(open val ui: Ui) : RecyclerView.ViewHolder(ui.root) {
        class Candidate(override val ui: LabeledCandidateItemUi) : UiHolder(ui)
        class Pagination(override val ui: PaginationUi) : UiHolder(ui)
    }

    private val candidatesAdapter = object : RecyclerView.Adapter<UiHolder>() {
        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long =
            data.candidates.getOrNull(position)?.hashCode()?.toLong() ?: PaginationRowId

        override fun getItemCount() =
            data.candidates.size + (if (showPaginationArrows && (data.hasPrev || data.hasNext)) 1 else 0)

        override fun getItemViewType(position: Int) = if (position < data.candidates.size) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UiHolder {
            return when (viewType) {
                0 -> UiHolder.Candidate(LabeledCandidateItemUi(ctx, theme, setupTextView, highlightCornerRadiusPx))
                else -> UiHolder.Pagination(PaginationUi(ctx, theme)).apply {
                    ui.prevIcon.setOnClickListener {
                        onPrevPage.invoke()
                    }
                    ui.nextIcon.setOnClickListener {
                        onNextPage.invoke()
                    }
                }
            }.apply {
                // assign default LayoutParams, otherwise updateLayoutParams won't work
                ui.root.layoutParams = FlexboxLayoutManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            }
        }

        override fun onBindViewHolder(holder: UiHolder, position: Int) {
            when (holder) {
                is UiHolder.Candidate -> {
                    bindCandidate(holder, position)
                    holder.ui.root.setOnClickListener {
                        onCandidateClick.invoke(position)
                    }
                    holder.ui.root.updateLayoutParams<FlexboxLayoutManager.LayoutParams> {
                        width = if (isVertical) MATCH_PARENT else WRAP_CONTENT
                    }
                }
                is UiHolder.Pagination -> {
                    holder.ui.update(data)
                    holder.ui.root.updateLayoutParams<FlexboxLayoutManager.LayoutParams> {
                        flexGrow = 1f
                        width = if (isVertical) MATCH_PARENT else WRAP_CONTENT
                        alignSelf = if (isVertical) AlignItems.STRETCH else AlignItems.CENTER
                    }
                }
            }
        }

        override fun onBindViewHolder(holder: UiHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains(SelectionPayload) && holder is UiHolder.Candidate) {
                bindCandidateSelection(holder, position)
                return
            }
            onBindViewHolder(holder, position)
        }

        override fun onViewRecycled(holder: UiHolder) {
            if (holder is UiHolder.Candidate) {
                holder.ui.root.setOnClickListener(null)
            }
            super.onViewRecycled(holder)
        }

        private fun bindCandidate(holder: UiHolder.Candidate, position: Int) {
            val candidate = data.candidates[position]
            holder.ui.update(
                candidate,
                active = highlightActive && position == data.cursorIndex,
                inactiveRow = !highlightActive,
                t9InputModeEnabled = t9InputModeEnabled,
                shortcutLabel = if (showShortcutLabels) shortcutLabelForPosition(position) else null
            )
        }

        private fun bindCandidateSelection(holder: UiHolder.Candidate, position: Int) {
            val candidate = data.candidates[position]
            holder.ui.updateSelection(
                candidate,
                active = highlightActive && position == data.cursorIndex,
                inactiveRow = !highlightActive,
                t9InputModeEnabled = t9InputModeEnabled,
                shortcutLabel = if (showShortcutLabels) shortcutLabelForPosition(position) else null
            )
        }
    }

    private val candidatesLayoutManager = FlexboxLayoutManager(ctx).apply {
        flexWrap = FlexWrap.WRAP
    }

    override val root = recyclerView {
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        adapter = candidatesAdapter
        layoutManager = candidatesLayoutManager
        overScrollMode = View.OVER_SCROLL_NEVER
        itemAnimator = null
        clipChildren = false
        clipToPadding = false
        setPadding(highlightOverflowPaddingPx, 0, highlightOverflowPaddingPx, 0)
    }

    fun update(
        data: FcitxEvent.PagedCandidateEvent.Data,
        orientation: FloatingCandidatesOrientation,
        showShortcutLabels: Boolean = false
    ) {
        val oldData = this.data
        val oldRows = rowsFor(oldData)
        val oldVertical = isVertical
        val oldShowShortcutLabels = this.showShortcutLabels
        val oldCursorIndex = oldData.cursorIndex
        val nextVertical = when (orientation) {
            FloatingCandidatesOrientation.Automatic -> data.layoutHint == LayoutHint.Vertical
            else -> orientation == FloatingCandidatesOrientation.Vertical
        }
        val layoutChanged = oldVertical != nextVertical ||
            oldShowShortcutLabels != showShortcutLabels
        this.data = data
        this.showShortcutLabels = showShortcutLabels
        this.isVertical = nextVertical
        candidatesLayoutManager.apply {
            if (isVertical) {
                flexDirection = FlexDirection.COLUMN
                alignItems = AlignItems.STRETCH
            } else {
                flexDirection = FlexDirection.ROW
                alignItems = if (showShortcutLabels) AlignItems.CENTER else AlignItems.BASELINE
            }
        }
        val newRows = rowsFor(data)
        when {
            layoutChanged -> candidatesAdapter.notifyDataSetChanged()
            oldRows == newRows -> notifyCursorChanged(oldCursorIndex, data.cursorIndex)
            else -> DiffUtil.calculateDiff(rowDiff(oldRows, newRows))
                .dispatchUpdatesTo(candidatesAdapter)
        }
    }

    fun setHighlightActive(active: Boolean) {
        if (highlightActive == active) return
        highlightActive = active
        if (data.candidates.isNotEmpty()) {
            candidatesAdapter.notifyItemRangeChanged(0, data.candidates.size, SelectionPayload)
        }
    }

    companion object {
        private const val PaginationRowId = Long.MIN_VALUE
        private val shortcutLabels = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        private fun shortcutLabelForPosition(position: Int): String? =
            shortcutLabels.getOrNull(position)

        private object SelectionPayload
    }

    private fun rowsFor(data: FcitxEvent.PagedCandidateEvent.Data): List<Row> {
        val rows = data.candidates.map { Row.Candidate(it) }.toMutableList<Row>()
        if (showPaginationArrows && (data.hasPrev || data.hasNext)) {
            rows += Row.Pagination(data.hasPrev, data.hasNext)
        }
        return rows
    }

    private fun notifyCursorChanged(oldCursorIndex: Int, newCursorIndex: Int) {
        if (oldCursorIndex == newCursorIndex) return
        if (oldCursorIndex in data.candidates.indices) {
            candidatesAdapter.notifyItemChanged(oldCursorIndex, SelectionPayload)
        }
        if (newCursorIndex in data.candidates.indices) {
            candidatesAdapter.notifyItemChanged(newCursorIndex, SelectionPayload)
        }
    }

    private fun rowDiff(oldRows: List<Row>, newRows: List<Row>) =
        object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldRows.size

            override fun getNewListSize(): Int = newRows.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldRows[oldItemPosition]
                val new = newRows[newItemPosition]
                return when {
                    old is Row.Candidate && new is Row.Candidate -> old.candidate == new.candidate
                    old is Row.Pagination && new is Row.Pagination -> true
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldRows[oldItemPosition] == newRows[newItemPosition]
        }
}

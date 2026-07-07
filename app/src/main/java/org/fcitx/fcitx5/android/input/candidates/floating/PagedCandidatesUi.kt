/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
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
    private val itemSpacingPx: Int,
    private val onCandidateClick: (Int) -> Unit,
    private val onPrevPage: () -> Unit,
    private val onNextPage: () -> Unit
) : Ui {

    private var data = FcitxEvent.PagedCandidateEvent.Data.Empty

    private var isVertical = false
    private var highlightActive = true
    private var showShortcutLabels = false
    private var shortcutLayout: ShortcutLayout? = null
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
    private var shortcutLabelPaddingApplied = false

    private sealed class Row {
        data class Candidate(
            val position: Int,
            val candidate: FcitxEvent.Candidate,
            val active: Boolean,
            val inactiveRow: Boolean,
            val shortcutLabel: String?
        ) : Row()
        data class Pagination(val hasPrev: Boolean, val hasNext: Boolean) : Row()
    }

    private var renderRows: List<Row> = rowsFor(data, highlightActive, showShortcutLabels)

    data class ShortcutLayout(
        val maxCandidateWidthPx: Int,
        val rowWidthPx: Int,
        val trailingPaddingPx: Int
    )

    sealed class UiHolder(open val ui: Ui) : RecyclerView.ViewHolder(ui.root) {
        class Candidate(override val ui: LabeledCandidateItemUi) : UiHolder(ui)
        class Pagination(override val ui: PaginationUi) : UiHolder(ui)
    }

    private val candidatesAdapter = object : RecyclerView.Adapter<UiHolder>() {
        override fun getItemCount() = renderRows.size

        override fun getItemViewType(position: Int) = when (renderRows[position]) {
            is Row.Candidate -> 0
            is Row.Pagination -> 1
        }

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
                    val row = candidateRowAt(position)
                    bindCandidate(holder, row)
                    holder.ui.root.setOnClickListener {
                        row?.let { onCandidateClick.invoke(it.position) }
                    }
                    holder.ui.root.updateLayoutParams<ViewGroup.LayoutParams> {
                        width = if (isVertical) MATCH_PARENT else WRAP_CONTENT
                    }
                }
                is UiHolder.Pagination -> {
                    val row = renderRows.getOrNull(position) as? Row.Pagination
                    holder.ui.update(
                        hasPrev = row?.hasPrev ?: false,
                        hasNext = row?.hasNext ?: false
                    )
                    holder.ui.root.updateLayoutParams<ViewGroup.LayoutParams> {
                        width = if (isVertical) MATCH_PARENT else WRAP_CONTENT
                    }
                    (holder.ui.root.layoutParams as? FlexboxLayoutManager.LayoutParams)?.apply {
                        flexGrow = 1f
                        alignSelf = if (isVertical) AlignItems.STRETCH else AlignItems.CENTER
                    }
                }
            }
        }

        override fun onBindViewHolder(holder: UiHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains(SelectionPayload) && holder is UiHolder.Candidate) {
                bindCandidateSelection(holder, candidateRowAt(position))
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

        private fun bindCandidate(holder: UiHolder.Candidate, row: Row.Candidate?) {
            row ?: return
            holder.ui.update(
                row.candidate,
                active = row.active,
                inactiveRow = row.inactiveRow,
                t9InputModeEnabled = t9InputModeEnabled,
                shortcutLabel = row.shortcutLabel,
                shortcutMaxWidthPx = shortcutLayout?.maxCandidateWidthPx
            )
        }

        private fun bindCandidateSelection(holder: UiHolder.Candidate, row: Row.Candidate?) {
            row ?: return
            holder.ui.updateSelection(
                row.candidate,
                active = row.active,
                inactiveRow = row.inactiveRow,
                t9InputModeEnabled = t9InputModeEnabled,
                shortcutLabel = row.shortcutLabel,
                shortcutMaxWidthPx = shortcutLayout?.maxCandidateWidthPx
            )
        }
    }

    private val flexboxLayoutManager = FlexboxLayoutManager(ctx).apply {
        flexWrap = FlexWrap.WRAP
    }
    private var shortcutToolbarLayoutActive = false

    private val recyclerRoot = recyclerView {
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        adapter = candidatesAdapter
        layoutManager = flexboxLayoutManager
        overScrollMode = View.OVER_SCROLL_NEVER
        itemAnimator = null
        clipChildren = false
        clipToPadding = false
        setPadding(highlightOverflowPaddingPx, 0, highlightOverflowPaddingPx, 0)
        addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val position = parent.getChildAdapterPosition(view)
                outRect.right = if (position != RecyclerView.NO_POSITION && position < state.itemCount - 1) {
                    itemSpacingPx
                } else {
                    0
                }
            }
        })
    }

    private val shortcutToolbarRoot = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        clipChildren = true
        clipToPadding = false
        visibility = View.GONE
        setPadding(highlightOverflowPaddingPx, 0, highlightOverflowPaddingPx, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
    }

    override val root = FrameLayout(ctx).apply {
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        clipChildren = false
        clipToPadding = false
        addView(recyclerRoot, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        addView(shortcutToolbarRoot, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    }
    private val shortcutCandidateItems = mutableListOf<LabeledCandidateItemUi>()
    private val shortcutPaginationItem = PaginationUi(ctx, theme).apply {
        prevIcon.setOnClickListener { onPrevPage.invoke() }
        nextIcon.setOnClickListener { onNextPage.invoke() }
    }

    fun update(
        data: FcitxEvent.PagedCandidateEvent.Data,
        orientation: FloatingCandidatesOrientation,
        showShortcutLabels: Boolean = false,
        shortcutLayout: ShortcutLayout? = null
    ) {
        val oldRows = renderRows
        val oldVertical = isVertical
        val oldShortcutLabels = this.showShortcutLabels
        val oldShortcutLayout = this.shortcutLayout
        val nextVertical = if (showShortcutLabels) {
            false
        } else when (orientation) {
            FloatingCandidatesOrientation.Automatic -> data.layoutHint == LayoutHint.Vertical
            else -> orientation == FloatingCandidatesOrientation.Vertical
        }
        val layoutChanged = oldVertical != nextVertical ||
            oldShortcutLabels != showShortcutLabels ||
            oldShortcutLayout != shortcutLayout
        this.data = data
        this.showShortcutLabels = showShortcutLabels
        this.shortcutLayout = shortcutLayout
        this.isVertical = nextVertical
        updateLayoutManager(showShortcutLabels)
        updateRootWidth(showShortcutLabels, shortcutLayout)
        updateShortcutToolbarClipping(showShortcutLabels)
        val newRows = rowsFor(
            data = data,
            highlightActive = highlightActive,
            showShortcutLabels = showShortcutLabels
        )
        updateShortcutLabelOverflowPadding(showShortcutLabels, shortcutLayout)
        renderRows = newRows
        if (showShortcutLabels) {
            renderShortcutToolbar(newRows, shortcutLayout)
            return
        }
        shortcutToolbarRoot.visibility = View.GONE
        recyclerRoot.visibility = View.VISIBLE
        when {
            layoutChanged -> candidatesAdapter.notifyDataSetChanged()
            oldRows == newRows -> Unit
            else -> DiffUtil.calculateDiff(rowDiff(oldRows, newRows))
                .dispatchUpdatesTo(candidatesAdapter)
        }
    }

    fun setHighlightActive(active: Boolean) {
        if (highlightActive == active) return
        val oldRows = renderRows
        highlightActive = active
        val newRows = rowsFor(
            data = data,
            highlightActive = highlightActive,
            showShortcutLabels = showShortcutLabels
        )
        renderRows = newRows
        if (showShortcutLabels) {
            renderShortcutToolbar(newRows, shortcutLayout)
            return
        }
        if (oldRows != newRows) {
            DiffUtil.calculateDiff(rowDiff(oldRows, newRows))
                .dispatchUpdatesTo(candidatesAdapter)
        }
    }

    private fun updateShortcutLabelOverflowPadding(
        enabled: Boolean,
        shortcutLayout: ShortcutLayout?
    ) {
        val trailingPadding = if (enabled) {
            shortcutLayout?.trailingPaddingPx?.coerceAtLeast(0) ?: 0
        } else {
            0
        }
        val verticalPadding = if (enabled) highlightOverflowPaddingPx else 0
        val rightPadding = highlightOverflowPaddingPx + trailingPadding
        if (
            shortcutLabelPaddingApplied == enabled &&
            shortcutToolbarRoot.paddingTop == verticalPadding &&
            shortcutToolbarRoot.paddingBottom == verticalPadding &&
            shortcutToolbarRoot.paddingLeft == highlightOverflowPaddingPx &&
            shortcutToolbarRoot.paddingRight == rightPadding
        ) return
        shortcutLabelPaddingApplied = enabled
        // T9 shortcut labels are a compact two-line surface. Keep a tiny bubble inset so font
        // metrics and the focus outline do not get clipped at the toolbar boundary. The
        // trailing reserve is a product-level rhythm choice: the last candidate always gets the
        // same breathing room instead of inheriting noise from width estimation.
        shortcutToolbarRoot.setPadding(
            highlightOverflowPaddingPx,
            verticalPadding,
            rightPadding,
            verticalPadding
        )
        if (!enabled) {
            recyclerRoot.setPadding(highlightOverflowPaddingPx, 0, highlightOverflowPaddingPx, 0)
        }
    }

    private fun updateRootWidth(
        showShortcutLabels: Boolean,
        shortcutLayout: ShortcutLayout?
    ) {
        val targetWidth = if (showShortcutLabels) {
            shortcutLayout?.rowWidthPx?.takeIf { it > 0 } ?: WRAP_CONTENT
        } else {
            WRAP_CONTENT
        }
        root.updateLayoutParams<ViewGroup.LayoutParams> {
            width = targetWidth
        }
    }

    private fun updateShortcutToolbarClipping(enabled: Boolean) {
        // T9 candidates are a paged toolbar, not a scrollable list. Clipping only in this mode
        // keeps normal floating candidates' focus overflow unchanged.
        recyclerRoot.clipChildren = enabled
        recyclerRoot.clipToPadding = false
        shortcutToolbarRoot.clipChildren = enabled
        shortcutToolbarRoot.clipToPadding = false
    }

    private fun updateLayoutManager(showShortcutLabels: Boolean) {
        if (showShortcutLabels) {
            shortcutToolbarLayoutActive = true
            return
        }
        if (shortcutToolbarLayoutActive) {
            shortcutToolbarLayoutActive = false
            recyclerRoot.layoutManager = flexboxLayoutManager
        }
        flexboxLayoutManager.apply {
            if (isVertical) {
                flexDirection = FlexDirection.COLUMN
                alignItems = AlignItems.STRETCH
            } else {
                flexDirection = FlexDirection.ROW
                alignItems = AlignItems.BASELINE
            }
        }
    }

    private fun renderShortcutToolbar(
        rows: List<Row>,
        shortcutLayout: ShortcutLayout?
    ) {
        recyclerRoot.visibility = View.GONE
        shortcutToolbarRoot.visibility = View.VISIBLE
        rows.forEachIndexed { index, row ->
            val view = when (row) {
                is Row.Candidate -> shortcutCandidateView(index, row, shortcutLayout)
                is Row.Pagination -> shortcutPaginationView(row)
            }
            attachShortcutChild(view, index, index == rows.lastIndex)
        }
        while (shortcutToolbarRoot.childCount > rows.size) {
            shortcutToolbarRoot.removeViewAt(shortcutToolbarRoot.childCount - 1)
        }
    }

    private fun shortcutCandidateView(
        displayIndex: Int,
        row: Row.Candidate,
        shortcutLayout: ShortcutLayout?
    ): View {
        val item = shortcutCandidateItems.getOrNull(displayIndex)
            ?: LabeledCandidateItemUi(ctx, theme, setupTextView, highlightCornerRadiusPx).also {
                shortcutCandidateItems += it
            }
        item.apply {
            update(
                row.candidate,
                active = row.active,
                inactiveRow = row.inactiveRow,
                t9InputModeEnabled = t9InputModeEnabled,
                shortcutLabel = row.shortcutLabel,
                shortcutMaxWidthPx = shortcutLayout?.maxCandidateWidthPx
            )
            root.setOnClickListener {
                onCandidateClick.invoke(row.position)
            }
        }
        return item.root
    }

    private fun shortcutPaginationView(row: Row.Pagination): View {
        return shortcutPaginationItem.apply {
            update(hasPrev = row.hasPrev, hasNext = row.hasNext)
        }.root
    }

    private fun attachShortcutChild(view: View, index: Int, last: Boolean) {
        val params = (view.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        val rightMargin = if (last) 0 else itemSpacingPx
        if (params.rightMargin != rightMargin) {
            params.rightMargin = rightMargin
            view.layoutParams = params
        }
        val existingIndex = shortcutToolbarRoot.indexOfChild(view)
        when {
            existingIndex == index -> Unit
            existingIndex >= 0 -> {
                shortcutToolbarRoot.removeViewAt(existingIndex)
                shortcutToolbarRoot.addView(view, index, params)
            }
            index < shortcutToolbarRoot.childCount -> shortcutToolbarRoot.addView(view, index, params)
            else -> shortcutToolbarRoot.addView(view, params)
        }
    }

    companion object {
        private val shortcutLabels = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        private fun shortcutLabelForPosition(position: Int): String? =
            shortcutLabels.getOrNull(position)

        private object SelectionPayload
    }

    private fun candidateRowAt(position: Int): Row.Candidate? =
        renderRows.getOrNull(position) as? Row.Candidate

    private fun rowsFor(
        data: FcitxEvent.PagedCandidateEvent.Data,
        highlightActive: Boolean,
        showShortcutLabels: Boolean
    ): List<Row> {
        val rows = data.candidates.mapIndexed { position, candidate ->
            Row.Candidate(
                position = position,
                candidate = candidate,
                active = highlightActive && position == data.cursorIndex,
                inactiveRow = !highlightActive,
                shortcutLabel = if (showShortcutLabels) shortcutLabelForPosition(position) else null
            )
        }.toMutableList<Row>()
        if (showPaginationArrows && (data.hasPrev || data.hasNext)) {
            rows += Row.Pagination(data.hasPrev, data.hasNext)
        }
        return rows
    }

    private fun rowDiff(oldRows: List<Row>, newRows: List<Row>) =
        object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldRows.size

            override fun getNewListSize(): Int = newRows.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldRows[oldItemPosition]
                val new = newRows[newItemPosition]
                return when {
                    old is Row.Candidate && new is Row.Candidate -> old.position == new.position
                    old is Row.Pagination && new is Row.Pagination -> true
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldRows[oldItemPosition] == newRows[newItemPosition]

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                val old = oldRows[oldItemPosition]
                val new = newRows[newItemPosition]
                return if (
                    old is Row.Candidate &&
                    new is Row.Candidate &&
                    old.candidate == new.candidate &&
                    old.shortcutLabel == new.shortcutLabel
                ) {
                    SelectionPayload
                } else {
                    null
                }
            }
        }
}

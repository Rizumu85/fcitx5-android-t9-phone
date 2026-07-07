/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.View.MeasureSpec
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.t9.T9ShortcutCandidateLayout
import kotlin.math.roundToInt
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui

class T9ShortcutCandidatesUi(
    override val ctx: Context,
    private val theme: Theme,
    private val setupTextView: TextView.() -> Unit,
    private val showPaginationArrows: Boolean,
    private val highlightCornerRadiusPx: Int,
    private val itemSpacingPx: Int,
    private val onCandidateClick: (Int) -> Unit,
    private val onPrevPage: () -> Unit,
    private val onNextPage: () -> Unit
) : Ui {

    private sealed class Row {
        data class Candidate(
            val position: Int,
            val candidate: FcitxEvent.Candidate,
            val active: Boolean,
            val inactiveRow: Boolean,
            val shortcutLabel: String
        ) : Row()
        data class Pagination(val hasPrev: Boolean, val hasNext: Boolean) : Row()
    }

    private var data = FcitxEvent.PagedCandidateEvent.Data.Empty
    private var highlightActive = true
    private var layout = T9ShortcutCandidateLayout(
        maxCandidateWidthPx = 0,
        rowWidthPx = 0,
        trailingPaddingPx = 0
    )
    private var renderRows: List<Row> = emptyList()
    var measuredToolbarWidthPx: Int? = null
        private set

    private val highlightOverflowPaddingPx =
        (highlightCornerRadiusPx * 0.35f).roundToInt().coerceAtLeast(ctx.dp(2))
    private val candidateItems = mutableListOf<LabeledCandidateItemUi>()
    private val paginationItem = PaginationUi(ctx, theme).apply {
        prevIcon.setOnClickListener { onPrevPage.invoke() }
        nextIcon.setOnClickListener { onNextPage.invoke() }
    }

    override val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        clipChildren = true
        clipToPadding = false
        visibility = View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
    }

    fun update(
        data: FcitxEvent.PagedCandidateEvent.Data,
        layout: T9ShortcutCandidateLayout
    ) {
        this.data = data
        this.layout = layout
        updateRootBounds(layout)
        render(rowsFor(data))
    }

    fun setHighlightActive(active: Boolean) {
        if (highlightActive == active) return
        highlightActive = active
        render(rowsFor(data))
    }

    private fun updateRootBounds(layout: T9ShortcutCandidateLayout) {
        val trailingPadding = layout.trailingPaddingPx.coerceAtLeast(0)
        val verticalPadding = highlightOverflowPaddingPx
        val rightPadding = highlightOverflowPaddingPx + trailingPadding
        if (
            root.paddingLeft != highlightOverflowPaddingPx ||
            root.paddingTop != verticalPadding ||
            root.paddingRight != rightPadding ||
            root.paddingBottom != verticalPadding
        ) {
            // Product decision: T9 shortcut candidates are a compact toolbar. The toolbar owns
            // its fixed trailing breath so the last candidate does not inherit text-width noise.
            root.setPadding(
                highlightOverflowPaddingPx,
                verticalPadding,
                rightPadding,
                verticalPadding
            )
        }
        val targetWidth = layout.rowWidthPx.takeIf { it > 0 } ?: WRAP_CONTENT
        root.updateLayoutParams<ViewGroup.LayoutParams> {
            width = targetWidth
        }
    }

    private fun render(rows: List<Row>) {
        renderRows = rows
        rows.forEachIndexed { index, row ->
            val view = when (row) {
                is Row.Candidate -> candidateView(index, row)
                is Row.Pagination -> paginationView(row)
            }
            attachChild(view, index, index == rows.lastIndex)
        }
        while (root.childCount > rows.size) {
            root.removeViewAt(root.childCount - 1)
        }
        // Product decision: the pinyin row should align with the rendered T9 toolbar, not with
        // a TextPaint estimate. Measuring the real pooled toolbar keeps the bubble width stable
        // while avoiding a hidden duplicate row.
        root.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        measuredToolbarWidthPx = root.measuredWidth.takeIf { it > 0 }
    }

    private fun candidateView(
        displayIndex: Int,
        row: Row.Candidate
    ): View {
        val item = candidateItems.getOrNull(displayIndex)
            ?: LabeledCandidateItemUi(ctx, theme, setupTextView, highlightCornerRadiusPx).also {
                candidateItems += it
            }
        item.apply {
            update(
                row.candidate,
                active = row.active,
                inactiveRow = row.inactiveRow,
                t9InputModeEnabled = true,
                shortcutLabel = row.shortcutLabel,
                shortcutMaxWidthPx = layout.maxCandidateWidthPx
            )
            root.setOnClickListener {
                onCandidateClick.invoke(row.position)
            }
        }
        return item.root
    }

    private fun paginationView(row: Row.Pagination): View {
        return paginationItem.apply {
            update(hasPrev = row.hasPrev, hasNext = row.hasNext)
        }.root
    }

    private fun attachChild(view: View, index: Int, last: Boolean) {
        val params = (view.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        val rightMargin = if (last) 0 else itemSpacingPx
        if (params.rightMargin != rightMargin) {
            params.rightMargin = rightMargin
            view.layoutParams = params
        }
        val existingIndex = root.indexOfChild(view)
        when {
            existingIndex == index -> Unit
            existingIndex >= 0 -> {
                root.removeViewAt(existingIndex)
                root.addView(view, index, params)
            }
            index < root.childCount -> root.addView(view, index, params)
            else -> root.addView(view, params)
        }
    }

    private fun rowsFor(data: FcitxEvent.PagedCandidateEvent.Data): List<Row> {
        val rows = data.candidates.mapIndexed { position, candidate ->
            Row.Candidate(
                position = position,
                candidate = candidate,
                active = highlightActive && position == data.cursorIndex,
                inactiveRow = !highlightActive,
                shortcutLabel = shortcutLabelForPosition(position)
            )
        }.toMutableList<Row>()
        if (showPaginationArrows && (data.hasPrev || data.hasNext)) {
            rows += Row.Pagination(data.hasPrev, data.hasNext)
        }
        return rows
    }

    companion object {
        private val shortcutLabels = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        private fun shortcutLabelForPosition(position: Int): String =
            shortcutLabels.getOrNull(position).orEmpty()
    }
}

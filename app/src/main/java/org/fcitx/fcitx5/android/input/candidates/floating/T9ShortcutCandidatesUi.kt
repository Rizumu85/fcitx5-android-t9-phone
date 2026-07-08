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
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.t9.T9ResponsivenessTrace
import org.fcitx.fcitx5.android.input.t9.T9ShortcutCandidateLayout
import org.fcitx.fcitx5.android.input.t9.T9ShortcutTailPolicy
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
        edgePaddingPx = 0,
        maxRowWidthPx = 0,
        trailingPaddingPx = 0
    )
    private var renderRows: List<Row> = emptyList()
    private var renderStructure: RenderStructure? = null
    var measuredToolbarWidthPx: Int? = null
        private set

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
        T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderCandidates.shortcutRootBounds") {
            updateRootBounds(layout)
        }
        val rows = T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderCandidates.shortcutRows") {
            rowsFor(data)
        }
        val nextStructure = renderStructureFor(rows, layout)
        if (nextStructure == renderStructure && root.childCount >= rows.size) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderCandidates.shortcutSelection") {
                renderSelection(rows)
            }
            publishPlannedToolbarWidth()
            return
        }
        T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderCandidates.shortcutRender") {
            render(rows, nextStructure)
        }
    }

    fun setHighlightActive(active: Boolean) {
        if (highlightActive == active) return
        highlightActive = active
        val rows = rowsFor(data)
        val nextStructure = renderStructureFor(rows, layout)
        if (nextStructure == renderStructure && root.childCount >= rows.size) {
            renderSelection(rows)
            publishPlannedToolbarWidth()
            return
        }
        render(rows, nextStructure)
    }

    private fun updateRootBounds(layout: T9ShortcutCandidateLayout) {
        val edgePadding = layout.edgePaddingPx.coerceAtLeast(0)
        val trailingPadding = layout.trailingPaddingPx.coerceAtLeast(0)
        val verticalPadding = edgePadding
        val rightPadding = edgePadding + trailingPadding
        if (
            root.paddingLeft != edgePadding ||
            root.paddingTop != verticalPadding ||
            root.paddingRight != rightPadding ||
            root.paddingBottom != verticalPadding
        ) {
            // Product decision: T9 shortcut candidates are a compact toolbar. The toolbar owns
            // its fixed trailing breath so the last candidate does not inherit text-width noise.
            root.setPadding(
                edgePadding,
                verticalPadding,
                rightPadding,
                verticalPadding
            )
        }
        val targetWidth = layout.rowWidthPx.takeIf { it > 0 } ?: WRAP_CONTENT
        if (root.layoutParams?.width != targetWidth) {
            root.updateLayoutParams<ViewGroup.LayoutParams> {
                width = targetWidth
            }
        }
    }

    private fun render(
        rows: List<Row>,
        structure: RenderStructure
    ) {
        T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderCandidates.shortcutBind") {
            rows.forEachIndexed { index, row ->
                val view = when (row) {
                    is Row.Candidate -> candidateView(
                        displayIndex = index,
                        row = row,
                        edgeAlignedEnd = T9ShortcutTailPolicy.edgeAlignsCandidateToBubbleTail(
                            isCandidate = true,
                            isLastVisibleItem = index == rows.lastIndex
                        )
                    )
                    is Row.Pagination -> paginationView(row)
                }
                attachChild(view, index, index == rows.lastIndex)
            }
        }
        T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderCandidates.shortcutTrim") {
            while (root.childCount > rows.size) {
                root.removeViewAt(root.childCount - 1)
            }
        }
        publishPlannedToolbarWidth()
        renderRows = rows
        renderStructure = structure
    }

    private fun renderSelection(rows: List<Row>) {
        val previousRows = renderRows
        rows.forEachIndexed { index, row ->
            val previous = previousRows.getOrNull(index)
            if (row !is Row.Candidate || previous !is Row.Candidate) return@forEachIndexed
            if (previous.active == row.active && previous.inactiveRow == row.inactiveRow) {
                return@forEachIndexed
            }
            candidateItems.getOrNull(index)?.updateSelection(
                candidate = row.candidate,
                active = row.active,
                inactiveRow = row.inactiveRow,
                t9InputModeEnabled = true,
                shortcutLabel = row.shortcutLabel,
                shortcutMaxWidthPx = layout.maxCandidateWidthPx
            )
        }
        renderRows = rows
    }

    private fun publishPlannedToolbarWidth() {
        measuredToolbarWidthPx = layout.rowWidthPx.takeIf { it > 0 }
    }

    private fun candidateView(
        displayIndex: Int,
        row: Row.Candidate,
        edgeAlignedEnd: Boolean
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
                shortcutMaxWidthPx = layout.maxCandidateWidthPx,
                shortcutEdgeAlignedEnd = edgeAlignedEnd
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

    private data class RenderStructure(
        val layout: T9ShortcutCandidateLayout,
        val rows: List<RowStructure>
    )

    private sealed class RowStructure {
        data class Candidate(
            val label: String,
            val text: String,
            val comment: String,
            val shortcutLabel: String
        ) : RowStructure()
        data class Pagination(val hasPrev: Boolean, val hasNext: Boolean) : RowStructure()
    }

    private fun renderStructureFor(
        rows: List<Row>,
        layout: T9ShortcutCandidateLayout
    ): RenderStructure =
        RenderStructure(
            layout = layout,
            rows = rows.map { row ->
                when (row) {
                    is Row.Candidate -> RowStructure.Candidate(
                        label = row.candidate.label,
                        text = row.candidate.text,
                        comment = row.candidate.comment,
                        shortcutLabel = row.shortcutLabel
                    )
                    is Row.Pagination -> RowStructure.Pagination(
                        hasPrev = row.hasPrev,
                        hasNext = row.hasNext
                    )
                }
            }
        )

    companion object {
        private val shortcutLabels = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        private fun shortcutLabelForPosition(position: Int): String =
            shortcutLabels.getOrNull(position).orEmpty()
    }
}

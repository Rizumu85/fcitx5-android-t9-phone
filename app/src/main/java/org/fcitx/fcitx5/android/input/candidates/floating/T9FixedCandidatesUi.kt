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
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.horizontalLayout
import kotlin.math.roundToInt

class T9FixedCandidatesUi(
    override val ctx: Context,
    private val theme: Theme,
    private val setupTextView: TextView.() -> Unit,
    private val showPaginationArrows: Boolean,
    private val highlightCornerRadiusPx: Int,
    private val itemSpacingPx: Int,
    private val maxCandidateWidthPx: () -> Int,
    private val onCandidateClick: (Int) -> Unit,
    private val onPrevPage: () -> Unit,
    private val onNextPage: () -> Unit
) : Ui {

    private var data = FcitxEvent.PagedCandidateEvent.Data.Empty
    private var highlightActive = true
    private var showShortcutLabels = true

    private val candidateItems = List(MaxCandidates) { index ->
        T9ShortcutCandidateItemUi(ctx, theme, setupTextView, highlightCornerRadiusPx).apply {
            root.setOnClickListener { onCandidateClick(index) }
            root.visibility = View.GONE
        }
    }

    private val paginationUi = PaginationUi(ctx, theme).apply {
        prevIcon.setOnClickListener { onPrevPage() }
        nextIcon.setOnClickListener { onNextPage() }
        root.visibility = View.GONE
    }

    private val highlightOverflowPaddingPx =
        (highlightCornerRadiusPx * 0.35f).roundToInt().coerceAtLeast(ctx.dp(2))

    override val root = horizontalLayout {
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(highlightOverflowPaddingPx, 0, highlightOverflowPaddingPx, 0)
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        candidateItems.forEach { item ->
            addView(
                item.root,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    rightMargin = itemSpacingPx
                }
            )
        }
        addView(
            paginationUi.root,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun update(
        data: FcitxEvent.PagedCandidateEvent.Data,
        showShortcutLabels: Boolean = true
    ) {
        this.data = data
        this.showShortcutLabels = showShortcutLabels
        render()
    }

    fun setHighlightActive(active: Boolean) {
        if (highlightActive == active) return
        highlightActive = active
        renderSelectionOnly()
    }

    private fun render() {
        val showPagination = showPaginationArrows && (data.hasPrev || data.hasNext)
        val lastVisibleCandidateIndex = data.candidates.lastIndex.coerceAtMost(candidateItems.lastIndex)
        candidateItems.forEachIndexed { index, item ->
            val candidate = data.candidates.getOrNull(index)
            if (candidate == null) {
                item.root.visibility = View.GONE
                return@forEachIndexed
            }
            item.root.visibility = View.VISIBLE
            setCandidateRightMargin(
                item.root,
                if (index < lastVisibleCandidateIndex || showPagination) itemSpacingPx else 0
            )
            item.update(
                candidate = candidate,
                active = highlightActive && index == data.cursorIndex,
                inactiveRow = !highlightActive,
                shortcutLabel = shortcutLabelForPosition(index) ?: "",
                shortcutMaxWidthPx = maxCandidateWidthPx()
            )
        }
        paginationUi.root.visibility = if (showPagination) View.VISIBLE else View.GONE
        paginationUi.update(data.hasPrev, data.hasNext)
    }

    private fun setCandidateRightMargin(view: View, rightMargin: Int) {
        val params = view.layoutParams as? LinearLayout.LayoutParams ?: return
        if (params.rightMargin == rightMargin) return
        params.rightMargin = rightMargin
        view.layoutParams = params
    }

    private fun renderSelectionOnly() {
        candidateItems.forEachIndexed { index, item ->
            val candidate = data.candidates.getOrNull(index) ?: return@forEachIndexed
            if (item.root.visibility != View.VISIBLE) return@forEachIndexed
            item.updateSelection(
                active = highlightActive && index == data.cursorIndex,
                inactiveRow = !highlightActive
            )
        }
    }

    companion object {
        const val MaxCandidates = 10
        private val shortcutLabels = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        private fun shortcutLabelForPosition(position: Int): String? =
            shortcutLabels.getOrNull(position)
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnPreDrawListener
import android.widget.FrameLayout
import android.widget.TextView

class T9PinyinRowAndroidAdapter(
    private val barView: View,
    private val rowWrapper: FrameLayout,
    private val overflowHint: TextView,
    private val chipAdapter: T9PinyinChipAdapter,
    private val window: Window,
    private val delegate: Delegate
) {
    interface Window {
        fun clear()
        fun submit(candidates: List<String>): T9PinyinRowWindow.VisibleState
        fun move(delta: Int): T9PinyinRowWindow.VisibleState?
        fun resetHighlight(): T9PinyinRowWindow.VisibleState?
        fun currentState(): T9PinyinRowWindow.VisibleState?
        fun highlightedPinyin(): String?
    }

    interface Delegate {
        val rowHeightPx: Int
        val rowGapPx: Int
        fun surfacePlan(
            state: T9PinyinRowWindow.VisibleState,
            renderedItems: List<String>,
            candidateRowWidthPx: Int?
        ): T9PinyinRowSurfacePlanner.Plan?
        fun viewportWidthPx(): Int?
        fun setCandidateRowTopOffset(offset: Int)
        fun isCandidateSurfaceWaitingForPosition(): Boolean
        fun showCandidateSurfaceWhenPositioned(contentReady: Boolean)
        fun requestCandidateSurfacePositionUpdate()
    }

    private var targetVisible = false
    private var revealPending = false
    private var revealPreDrawListener: OnPreDrawListener? = null
    private var renderedWindowStart = 0

    var renderedItems: List<String> = emptyList()
        private set

    var usesWindowedDisplay: Boolean = false
        private set

    fun render(candidates: List<String>, useT9: Boolean): Boolean {
        if (!useT9 || candidates.isEmpty()) {
            clearWindowAndRenderedState()
            return setVisible(false)
        }
        val state = T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.window") {
            window.submit(candidates)
        }
        val previousWindowStart = renderedWindowStart
        val ready = renderWindow(state)
        if (state.windowStart != previousWindowStart) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.scroll") {
                chipAdapter.scrollToStart()
            }
        }
        return ready
    }

    fun syncVisibleLayout(): Boolean {
        if (!targetVisible) return true
        window.currentState()?.let {
            // Product decision: candidate width changes can resize the second bubble even when
            // the pinyin text itself is unchanged. Re-apply one render-ready snapshot before
            // width sync so the row and bubble reveal from the same visual state.
            renderWindow(
                state = it,
                candidateRowWidthPx = null,
                resetScrollOnChange = false,
                updateVisibility = false
            )
        }
        val widthReady = syncWidthToCandidates()
        setRowHeight(delegate.rowHeightPx)
        if (widthReady && rowWrapper.visibility == View.INVISIBLE) {
            scheduleReveal()
        }
        return widthReady
    }

    fun moveHighlighted(delta: Int): Boolean {
        val state = window.move(delta) ?: return false
        renderWindow(state)
        if (usesWindowedDisplay) {
            chipAdapter.scrollToStart()
        } else {
            chipAdapter.scrollToHighlighted(delegate.viewportWidthPx())
        }
        return true
    }

    fun renderFocus(focus: T9CandidateFocus, previousFocus: T9CandidateFocus) {
        val topFocused = focus == T9CandidateFocus.TOP
        if (topFocused && previousFocus != T9CandidateFocus.TOP) {
            updateOverflowHint(false)
            window.resetHighlight()?.let(::renderWindow)
            chipAdapter.scrollToStart()
        } else if (!topFocused) {
            window.currentState()?.let(::renderWindow)
        }
        chipAdapter.setHighlightActive(topFocused)
    }

    fun clear() {
        clearWindowAndRenderedState()
        setVisible(false)
    }

    fun removeRevealListener() {
        revealPreDrawListener?.let { listener ->
            if (rowWrapper.viewTreeObserver.isAlive) {
                rowWrapper.viewTreeObserver.removeOnPreDrawListener(listener)
            }
        }
        revealPreDrawListener = null
        revealPending = false
    }

    fun currentWindowStateForLayout(): T9PinyinRowWindow.VisibleState? =
        window.currentState()
            ?: renderedItems
                .takeIf { it.isNotEmpty() }
                ?.let {
                    T9PinyinRowWindow.VisibleState(
                        items = it,
                        highlightedIndex = 0,
                        windowStart = renderedWindowStart
                    )
                }

    fun highlightedPinyin(): String? = window.highlightedPinyin()

    private fun clearWindowAndRenderedState() {
        window.clear()
        renderedWindowStart = 0
        renderedItems = emptyList()
        usesWindowedDisplay = false
        updateOverflowHint(false)
    }

    private fun renderWindow(state: T9PinyinRowWindow.VisibleState): Boolean =
        renderWindow(
            state = state,
            candidateRowWidthPx = null,
            resetScrollOnChange = true,
            updateVisibility = true
        )

    private fun renderWindow(
        state: T9PinyinRowWindow.VisibleState,
        candidateRowWidthPx: Int?,
        resetScrollOnChange: Boolean,
        updateVisibility: Boolean
    ): Boolean {
        renderedItems = state.items
        val surfacePlan = delegate.surfacePlan(
            state = state,
            renderedItems = renderedItems,
            candidateRowWidthPx = candidateRowWidthPx
        ) ?: run {
            if (updateVisibility) {
                waitForLayout()
            }
            return false
        }
        surfacePlan.rowWidthPx?.let(::setRowWidth)
        usesWindowedDisplay = surfacePlan.usesWindowedDisplay
        updateOverflowHint(
            visible = surfacePlan.showOverflowHint,
            startPx = surfacePlan.overflowHintStartPx
        )
        setBarWidth(surfacePlan.pinyinBarWidthPx)
        val changed = T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.submit") {
            chipAdapter.submitList(surfacePlan.displayedItems, surfacePlan.displayedHighlight)
        }
        val ready = if (updateVisibility) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.visibility") {
                setVisible(true)
            }
        } else {
            true
        }
        if (changed && resetScrollOnChange) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.scroll") {
                chipAdapter.scrollToStart()
            }
        }
        renderedWindowStart = state.windowStart
        return ready
    }

    private fun syncWidthToCandidates(): Boolean {
        val state = currentWindowStateForLayout() ?: return false
        val plan = delegate.surfacePlan(
            state = state,
            renderedItems = renderedItems,
            candidateRowWidthPx = null
        )?.takeIf { it.contentReady } ?: return false
        val width = plan.rowWidthPx ?: return false
        setRowWidth(width)
        return true
    }

    private fun showNow() {
        setRowHeight(delegate.rowHeightPx)
        barView.visibility = View.VISIBLE
        rowWrapper.visibility = View.VISIBLE
        if (delegate.isCandidateSurfaceWaitingForPosition()) {
            delegate.showCandidateSurfaceWhenPositioned(true)
        }
    }

    private fun scheduleReveal() {
        if (revealPending) return
        revealPending = true
        val listener = object : OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!targetVisible) {
                    removeRevealListener()
                    return true
                }
                setRowHeight(delegate.rowHeightPx)
                if (!isReadyForReveal()) {
                    delegate.requestCandidateSurfacePositionUpdate()
                    return true
                }
                removeRevealListener()
                showNow()
                return true
            }
        }
        revealPreDrawListener = listener
        rowWrapper.viewTreeObserver.addOnPreDrawListener(listener)
        rowWrapper.requestLayout()
        rowWrapper.invalidate()
    }

    private fun isReadyForReveal(): Boolean {
        if (!syncWidthToCandidates()) return false
        if (barView.width <= 0 && barView.measuredWidth <= 0) return false
        if (rowWrapper.width <= 0 && rowWrapper.measuredWidth <= 0) return false
        if (chipAdapter.itemCount != renderedItems.size) return false
        return chipAdapter.laidOutContentWidthPx()?.let { it > 0 } == true
    }

    private fun waitForLayout() {
        targetVisible = true
        resetTransform()
        setRowHeight(delegate.rowHeightPx)
        // Product requirement: the first pinyin-filter frame must be complete. Keep the row in
        // layout but invisible until both the Hanzi row width and chip placement are known.
        barView.visibility = View.INVISIBLE
        rowWrapper.visibility = View.INVISIBLE
    }

    private fun setVisible(visible: Boolean): Boolean {
        val snapshot = visibilitySnapshot()
        val rowAlreadyVisible =
            snapshot.wrapperVisibility == T9PinyinRowVisibilityPlanner.Visibility.VISIBLE &&
                snapshot.barVisibility == T9PinyinRowVisibilityPlanner.Visibility.VISIBLE
        val widthReady = visible && !rowAlreadyVisible && syncWidthToCandidates()
        val action = T9PinyinRowVisibilityPlanner.planSetVisible(
            requestedVisible = visible,
            snapshot = snapshot,
            widthReady = widthReady
        )
        if (action == T9PinyinRowVisibilityPlanner.SetVisibleAction.NOOP_READY) return true
        targetVisible = visible

        return when (action) {
            T9PinyinRowVisibilityPlanner.SetVisibleAction.NOOP_READY -> true
            T9PinyinRowVisibilityPlanner.SetVisibleAction.SYNC_VISIBLE_LAYOUT ->
                syncVisibleLayout()
            T9PinyinRowVisibilityPlanner.SetVisibleAction.WAIT_FOR_LAYOUT,
            T9PinyinRowVisibilityPlanner.SetVisibleAction.WAIT_FOR_WIDTH -> {
                waitForLayout()
                rowWrapper.post {
                    when (T9PinyinRowVisibilityPlanner.planDeferredWidth(
                        targetVisible = targetVisible,
                        widthReady = targetVisible && syncWidthToCandidates()
                    )) {
                        T9PinyinRowVisibilityPlanner.DeferredWidthAction.SHOW_NOW -> {
                            setRowHeight(delegate.rowHeightPx)
                            scheduleReveal()
                        }
                        T9PinyinRowVisibilityPlanner.DeferredWidthAction.KEEP_WAITING ->
                            setRowHeight(delegate.rowHeightPx)
                        T9PinyinRowVisibilityPlanner.DeferredWidthAction.IGNORE -> Unit
                    }
                }
                false
            }
            T9PinyinRowVisibilityPlanner.SetVisibleAction.HIDE_NOW -> {
                removeRevealListener()
                chipAdapter.clear()
                chipAdapter.scrollToStart()
                renderedItems = emptyList()
                usesWindowedDisplay = false
                updateOverflowHint(false)
                barView.visibility = View.GONE
                rowWrapper.visibility = View.GONE
                setRowHeight(0)
                true
            }
        }
    }

    private fun resetTransform() {
        barView.alpha = 1f
        barView.scaleX = 1f
        barView.translationY = 0f
    }

    private fun visibilitySnapshot(): T9PinyinRowVisibilityPlanner.Snapshot =
        T9PinyinRowVisibilityPlanner.Snapshot(
            targetVisible = targetVisible,
            wrapperVisibility = rowWrapper.visibility.toT9PinyinRowVisibility(),
            barVisibility = barView.visibility.toT9PinyinRowVisibility()
        )

    private fun Int.toT9PinyinRowVisibility(): T9PinyinRowVisibilityPlanner.Visibility =
        when (this) {
            View.VISIBLE -> T9PinyinRowVisibilityPlanner.Visibility.VISIBLE
            View.INVISIBLE -> T9PinyinRowVisibilityPlanner.Visibility.INVISIBLE
            else -> T9PinyinRowVisibilityPlanner.Visibility.GONE
        }

    private fun setRowHeight(height: Int) {
        rowWrapper.minimumHeight = height
        (barView.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.height != height) {
                params.height = height
                barView.layoutParams = params
            }
        }
        (rowWrapper.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.height != height) {
                params.height = height
                rowWrapper.layoutParams = params
            }
        }
        delegate.setCandidateRowTopOffset(if (height > 0) height + delegate.rowGapPx else 0)
    }

    private fun setRowWidth(width: Int) {
        setBarWidth(width)
        (rowWrapper.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.width != width) {
                params.width = width
                rowWrapper.layoutParams = params
            }
        }
    }

    private fun setBarWidth(width: Int) {
        (barView.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.width != width) {
                params.width = width
                barView.layoutParams = params
            }
        }
    }

    private fun updateOverflowHint(visible: Boolean, startPx: Int = 0) {
        val targetVisibility = if (visible) View.VISIBLE else View.GONE
        if (overflowHint.visibility != targetVisibility) {
            overflowHint.visibility = targetVisibility
        }
        (overflowHint.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            val targetMargin = if (visible) startPx else 0
            val targetGravity = Gravity.START or Gravity.CENTER_VERTICAL
            if (params.leftMargin != targetMargin || params.gravity != targetGravity) {
                params.leftMargin = targetMargin
                params.gravity = targetGravity
                overflowHint.layoutParams = params
            }
        }
        if (barView.paddingRight != 0) {
            barView.setPadding(0, 0, 0, 0)
        }
        (barView as? ViewGroup)?.clipToPadding = false
    }
}

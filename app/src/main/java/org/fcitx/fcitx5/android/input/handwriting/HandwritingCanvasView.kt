/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import kotlin.math.hypot
import kotlin.math.min

/** Low-latency ink surface that keeps recognition geometry separate from rendered brush geometry. */
class HandwritingCanvasView(context: Context) : FrameLayout(context) {
    var onStrokeFinished: (HandwritingStroke) -> Unit = {}

    var brushColor: Int = 0

    private val inkView = InProgressStrokesView(context)
    private val activePoints = mutableListOf<HandwritingPoint>()
    private val renderLedger = HandwritingStrokeRenderLedger<InProgressStrokeId>()
    private var activeStrokeId: InProgressStrokeId? = null

    init {
        addView(inkView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        inkView.addFinishedStrokesListener(
            object : InProgressStrokesFinishedListener {
                override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                    removeFinishedStrokes(renderLedger.acceptFinished(strokes.keys))
                }
            }
        )
    }

    fun setCompletedStrokes(strokes: List<HandwritingStroke>) {
        // Coordinator state only appends, undoes, or clears strokes. Ink owns their visual
        // geometry, so count-based reconciliation avoids rebuilding paths on every state publish.
        removeFinishedStrokes(renderLedger.reconcileCoordinatorCount(strokes.size))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // AndroidX Ink initialization is intentionally paid before the user's first down event.
        post(inkView::eagerInit)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginStroke(event)
            MotionEvent.ACTION_MOVE -> continueStroke(event)
            MotionEvent.ACTION_UP -> finishStroke(event)
            MotionEvent.ACTION_CANCEL -> cancelStroke(event)
            else -> return false
        }
        return true
    }

    private fun beginStroke(event: MotionEvent) {
        parent?.requestDisallowInterceptTouchEvent(true)
        requestUnbufferedDispatch(event)
        activePoints.clear()
        appendEventPoints(event, includeHistory = false)
        activeStrokeId = inkView.startStroke(event, event.getPointerId(0), createBrush())
    }

    private fun continueStroke(event: MotionEvent) {
        val strokeId = activeStrokeId ?: return
        appendEventPoints(event, includeHistory = true)
        inkView.addToStroke(event, event.getPointerId(0), strokeId)
    }

    private fun finishStroke(event: MotionEvent) {
        val strokeId = activeStrokeId ?: return
        appendEventPoints(event, includeHistory = true)
        renderLedger.expectLocalStrokeCompletion()
        inkView.finishStroke(event, event.getPointerId(0), strokeId)
        activeStrokeId = null
        parent?.requestDisallowInterceptTouchEvent(false)
        val stroke = HandwritingStroke(activePoints.toList())
        activePoints.clear()
        if (stroke.points.isNotEmpty()) onStrokeFinished(stroke)
    }

    private fun cancelStroke(event: MotionEvent) {
        activeStrokeId?.let { inkView.cancelStroke(it, event) }
        activeStrokeId = null
        activePoints.clear()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun appendEventPoints(event: MotionEvent, includeHistory: Boolean) {
        if (includeHistory) {
            for (historyIndex in 0 until event.historySize) {
                appendPoint(
                    event.getHistoricalX(0, historyIndex),
                    event.getHistoricalY(0, historyIndex),
                    event.getHistoricalEventTime(historyIndex),
                    event.getHistoricalPressure(0, historyIndex)
                )
            }
        }
        appendPoint(event.x, event.y, event.eventTime, event.pressure)
    }

    private fun appendPoint(x: Float, y: Float, timeMillis: Long, pressure: Float) {
        val previous = activePoints.lastOrNull()
        if (previous != null && hypot(x - previous.x, y - previous.y) < MinRecognitionPointDistancePx) {
            return
        }
        activePoints += HandwritingPoint(x, y, timeMillis, pressure.coerceIn(0.2f, 1.5f))
    }

    private fun createBrush(): Brush = Brush.createWithColorIntArgb(
        StockBrushes.pressurePen(),
        brushColor,
        (min(width, height) * BrushWidthRatio).coerceIn(MinBrushWidthPx, MaxBrushWidthPx),
        BrushEpsilon
    )

    private fun removeFinishedStrokes(ids: Set<InProgressStrokeId>) {
        if (ids.isNotEmpty()) inkView.removeFinishedStrokes(ids)
    }

    private companion object {
        const val BrushWidthRatio = 0.026f
        const val MinBrushWidthPx = 7f
        const val MaxBrushWidthPx = 14f
        const val BrushEpsilon = 0.1f
        const val MinRecognitionPointDistancePx = 0.8f
    }
}

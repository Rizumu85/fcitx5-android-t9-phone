/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import kotlin.math.hypot
import kotlin.math.min

/** Low-latency ink surface that keeps recognition geometry separate from rendered brush geometry. */
class HandwritingCanvasView(
    context: Context,
    private val brushStyleProvider: () -> HandwritingBrushStyle = {
        HandwritingBrushStyle.CALLIGRAPHY
    }
) : FrameLayout(context) {
    var onStrokeStarted: () -> Unit = {}
    var onStrokeFinished: (HandwritingStroke) -> Unit = {}

    var brushColor: Int = 0

    private val finishedStrokesView = FinishedHandwritingStrokesView(context)
    private val inkView = createInkView(context)
    private val motionPredictor = MotionEventPredictor.newInstance(this)
    private val activePoints = mutableListOf<HandwritingPoint>()
    private val renderLedger = HandwritingStrokeRenderLedger<InProgressStrokeId, Stroke>()
    private var activeStrokeId: InProgressStrokeId? = null
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var cachedBrushKey: BrushKey? = null
    private var cachedBrush: Brush? = null

    init {
        addView(
            finishedStrokesView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(inkView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        inkView.addFinishedStrokesListener(
            object : InProgressStrokesFinishedListener {
                override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                    // Completed geometry moves off the front buffer immediately. Keeping only the
                    // active stroke there follows Ink's intended fast path and bounds move cost.
                    finishedStrokesView.setStrokes(renderLedger.acceptFinished(strokes))
                    inkView.removeFinishedStrokes(strokes.keys)
                }
            }
        )
    }

    fun setCompletedStrokes(strokes: List<HandwritingStroke>) {
        // Coordinator state only appends, undoes, or clears strokes. Ink owns their visual
        // geometry, so count-based reconciliation avoids rebuilding paths on every state publish.
        finishedStrokesView.setStrokes(renderLedger.reconcileCoordinatorCount(strokes.size))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Entering handwriting is the point at which drawing becomes likely. Initialize
        // synchronously here so a fast first down cannot overtake a queued warmup callback.
        inkView.eagerInit()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0) {
            // Geometry is known before the tray becomes interactive. Paying the immutable brush
            // setup here keeps AndroidX Ink class loading and JNI work out of ACTION_DOWN.
            createBrush()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        motionPredictor.record(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginStroke(event)
            MotionEvent.ACTION_MOVE -> continueStroke(event)
            MotionEvent.ACTION_UP -> finishStroke(event)
            MotionEvent.ACTION_CANCEL -> cancelStroke(event)
            MotionEvent.ACTION_POINTER_DOWN -> cancelStroke(event)
            MotionEvent.ACTION_POINTER_UP -> Unit
            else -> return false
        }
        return true
    }

    private fun beginStroke(event: MotionEvent) {
        parent?.requestDisallowInterceptTouchEvent(true)
        requestUnbufferedDispatch(event)
        // Recognition must be invalidated on ACTION_DOWN rather than after the stroke completes;
        // otherwise a partial-character result can relayout the candidate bubble under the pen.
        onStrokeStarted()
        activePoints.clear()
        val pointerIndex = event.actionIndex
        activePointerId = event.getPointerId(pointerIndex)
        appendEventPoints(event, pointerIndex, includeHistory = false)
        activeStrokeId = inkView.startStroke(event, activePointerId, createBrush())
    }

    private fun continueStroke(event: MotionEvent) {
        val strokeId = activeStrokeId ?: return
        val pointerIndex = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: return
        appendEventPoints(event, pointerIndex, includeHistory = true)
        val predictedEvent = motionPredictor.predict()
        try {
            // Prediction fills the display-frame gap without polluting recognition geometry.
            inkView.addToStroke(event, activePointerId, strokeId, predictedEvent)
        } finally {
            predictedEvent?.recycle()
        }
    }

    private fun finishStroke(event: MotionEvent) {
        val strokeId = activeStrokeId ?: return
        val pointerIndex = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: return
        appendEventPoints(event, pointerIndex, includeHistory = true)
        renderLedger.expectLocalStrokeCompletion(strokeId)
        inkView.finishStroke(event, activePointerId, strokeId)
        activeStrokeId = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
        val stroke = HandwritingStroke(activePoints.toList())
        activePoints.clear()
        if (stroke.points.isNotEmpty()) onStrokeFinished(stroke)
    }

    private fun cancelStroke(event: MotionEvent) {
        activeStrokeId?.let { inkView.cancelStroke(it, event) }
        activeStrokeId = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activePoints.clear()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun appendEventPoints(
        event: MotionEvent,
        pointerIndex: Int,
        includeHistory: Boolean
    ) {
        if (includeHistory) {
            for (historyIndex in 0 until event.historySize) {
                appendPoint(
                    event.getHistoricalX(pointerIndex, historyIndex),
                    event.getHistoricalY(pointerIndex, historyIndex),
                    event.getHistoricalEventTime(historyIndex),
                    event.getHistoricalPressure(pointerIndex, historyIndex)
                )
            }
        }
        appendPoint(
            event.getX(pointerIndex),
            event.getY(pointerIndex),
            event.eventTime,
            event.getPressure(pointerIndex)
        )
    }

    private fun appendPoint(x: Float, y: Float, timeMillis: Long, pressure: Float) {
        val previous = activePoints.lastOrNull()
        if (previous != null && hypot(x - previous.x, y - previous.y) < MinRecognitionPointDistancePx) {
            return
        }
        activePoints += HandwritingPoint(x, y, timeMillis, pressure.coerceIn(0.2f, 1.5f))
    }

    private fun createBrush(): Brush {
        val minimumDimension = min(width, height)
        // Read once per stroke. This keeps move handling allocation-free while still honoring a
        // setting changed while Android merely hides, rather than recreates, the IME window.
        val style = brushStyleProvider()
        val key = BrushKey(style, brushColor, minimumDimension)
        if (key == cachedBrushKey) return requireNotNull(cachedBrush)
        val (family, size) = when (style) {
            HandwritingBrushStyle.PEN -> StockBrushes.marker() to
                (minimumDimension * PenWidthRatio).coerceIn(MinPenWidthPx, MaxPenWidthPx)
            HandwritingBrushStyle.CALLIGRAPHY -> StockBrushes.pressurePen() to
                (minimumDimension * CalligraphyWidthRatio)
                    .coerceIn(MinCalligraphyWidthPx, MaxCalligraphyWidthPx)
        }
        return Brush.createWithColorIntArgb(family, brushColor, size, BrushEpsilon).also {
            // Brush construction crosses the Ink JNI seam. Cache the immutable result so the
            // first-down path only pays again after a visible style, color, or size change.
            cachedBrushKey = key
            cachedBrush = it
        }
    }

    private data class BrushKey(
        val style: HandwritingBrushStyle,
        val color: Int,
        val minimumDimension: Int
    )

    @SuppressLint("RestrictedApi")
    @Suppress("DEPRECATION")
    private fun createInkView(context: Context) = InProgressStrokesView(context).apply {
        // Several target T9 phones expose Android 14 over older vendor graphics stacks. Ink's
        // SurfaceControl front buffer can then show the first sample but defer later samples until
        // handoff. The HWUI renderer keeps the same brush geometry while presenting every move in
        // the ordinary view hierarchy, which is more important here than saving a single frame.
        useHighLatencyRenderHelper = true
    }

    private companion object {
        const val PenWidthRatio = 0.018f
        const val MinPenWidthPx = 5.5f
        const val MaxPenWidthPx = 9f
        const val CalligraphyWidthRatio = 0.04f
        const val MinCalligraphyWidthPx = 11f
        const val MaxCalligraphyWidthPx = 20f
        const val BrushEpsilon = 0.1f
        const val MinRecognitionPointDistancePx = 0.8f
    }
}

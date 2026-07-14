/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

class HandwritingCanvasView(context: Context) : View(context) {
    var onStrokeFinished: (HandwritingStroke) -> Unit = {}

    var brushColor: Int
        get() = paint.color
        set(value) {
            paint.color = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var completedStrokes: List<HandwritingStroke> = emptyList()
    private var completedGeometry: List<RenderedStroke> = emptyList()
    private val activePoints = mutableListOf<HandwritingPoint>()

    fun setCompletedStrokes(strokes: List<HandwritingStroke>) {
        if (completedStrokes == strokes) return
        completedStrokes = strokes
        rebuildCompletedGeometry()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        completedGeometry.forEach { drawRenderedStroke(canvas, it) }
        drawLiveStroke(canvas, activePoints)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        rebuildCompletedGeometry()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activePoints.clear()
                appendPoint(event.x, event.y, event.eventTime, event.pressure)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (historyIndex in 0 until event.historySize) {
                    appendPoint(
                        event.getHistoricalX(0, historyIndex),
                        event.getHistoricalY(0, historyIndex),
                        event.getHistoricalEventTime(historyIndex),
                        event.getHistoricalPressure(0, historyIndex)
                    )
                }
                appendPoint(event.x, event.y, event.eventTime, event.pressure)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                appendPoint(event.x, event.y, event.eventTime, event.pressure)
                val stroke = HandwritingStroke(activePoints.toList())
                activePoints.clear()
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                if (stroke.points.isNotEmpty()) onStrokeFinished(stroke)
            }
            MotionEvent.ACTION_CANCEL -> {
                activePoints.clear()
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
            }
            else -> return false
        }
        return true
    }

    private fun appendPoint(x: Float, y: Float, timeMillis: Long, pressure: Float) {
        val previous = activePoints.lastOrNull()
        if (previous != null && hypot(x - previous.x, y - previous.y) < MinPointDistancePx) return
        activePoints += HandwritingPoint(x, y, timeMillis, pressure.coerceIn(0.2f, 1.5f))
    }

    private fun rebuildCompletedGeometry() {
        completedGeometry = if (width <= 0 || height <= 0) {
            emptyList()
        } else {
            completedStrokes.map { stroke ->
                RenderedStroke(stroke.points, strokeWidths(stroke.points))
            }
        }
    }

    private fun drawLiveStroke(canvas: Canvas, points: List<HandwritingPoint>) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            paint.strokeWidth = baseBrushWidth()
            canvas.drawPoint(points[0].x, points[0].y, paint)
            return
        }
        val baseWidth = baseBrushWidth()
        var smoothedWidth = baseWidth
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            smoothedWidth = nextStrokeWidth(points, index, baseWidth, smoothedWidth)
            paint.strokeWidth = smoothedWidth
            canvas.drawLine(previous.x, previous.y, current.x, current.y, paint)
        }
    }

    private fun drawRenderedStroke(canvas: Canvas, stroke: RenderedStroke) {
        if (stroke.points.size == 1) {
            paint.strokeWidth = baseBrushWidth()
            canvas.drawPoint(stroke.points[0].x, stroke.points[0].y, paint)
            return
        }
        for (index in 1 until stroke.points.size) {
            val previous = stroke.points[index - 1]
            val current = stroke.points[index]
            paint.strokeWidth = stroke.widths[index - 1]
            canvas.drawLine(previous.x, previous.y, current.x, current.y, paint)
        }
    }

    private fun strokeWidths(points: List<HandwritingPoint>): FloatArray {
        if (points.size < 2) return FloatArray(0)
        val baseWidth = baseBrushWidth()
        var smoothedWidth = baseWidth
        return FloatArray(points.size - 1) { segmentIndex ->
            val index = segmentIndex + 1
            smoothedWidth = nextStrokeWidth(points, index, baseWidth, smoothedWidth)
            smoothedWidth
        }
    }

    private fun nextStrokeWidth(
        points: List<HandwritingPoint>,
        index: Int,
        baseWidth: Float,
        previousWidth: Float
    ): Float {
        val previous = points[index - 1]
        val current = points[index]
        val distance = hypot(current.x - previous.x, current.y - previous.y)
        val elapsed = (current.timeMillis - previous.timeMillis).coerceAtLeast(1L)
        val velocity = distance / elapsed
        val velocityFactor = (1.12f - velocity.coerceIn(0f, 2.2f) * 0.22f)
            .coerceIn(0.64f, 1.12f)
        val pressureFactor = 0.86f + current.pressure.coerceIn(0.2f, 1.2f) * 0.18f
        val taper = when (points.lastIndex - index) {
            0 -> 0.76f
            1 -> 0.9f
            else -> 1f
        }
        val targetWidth = baseWidth * velocityFactor * pressureFactor * taper
        return previousWidth + (targetWidth - previousWidth) * WidthSmoothing
    }

    private fun baseBrushWidth(): Float =
        (min(width, height) * BrushWidthRatio).coerceIn(4.5f, 10f)

    private data class RenderedStroke(
        val points: List<HandwritingPoint>,
        val widths: FloatArray
    )

    private companion object {
        const val BrushWidthRatio = 0.018f
        const val WidthSmoothing = 0.38f
        const val MinPointDistancePx = 0.8f
    }
}

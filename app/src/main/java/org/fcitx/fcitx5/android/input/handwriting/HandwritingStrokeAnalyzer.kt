/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 *
 * The substroke analysis is an independent Kotlin adaptation of the algorithm
 * documented by gugray/hanzi_lookup and HanziLookup.
 */

package org.fcitx.fcitx5.android.input.handwriting

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class HandwritingSubStroke(
    val direction: Int,
    val length: Int,
    val centerX: Int,
    val centerY: Int
)

internal object HandwritingStrokeAnalyzer {
    private const val CanvasSize = 255f
    private const val MinSegmentLength = 12.5f
    private const val MaxLocalLengthRatio = 1.1f
    private const val MaxRunningLengthRatio = 1.09f

    data class Result(
        val strokeCount: Int,
        val subStrokes: List<HandwritingSubStroke>
    )

    fun analyze(strokes: List<HandwritingStroke>): Result {
        val normalized = normalize(strokes)
        return Result(
            strokeCount = normalized.size,
            subStrokes = normalized.flatMap(::analyzeStroke)
        )
    }

    private fun normalize(strokes: List<HandwritingStroke>): List<List<Point>> {
        val source = strokes.mapNotNull { stroke ->
            stroke.points.takeIf { it.isNotEmpty() }?.map { Point(it.x, it.y) }
        }
        if (source.isEmpty()) return emptyList()
        val all = source.flatten()
        val minX = all.minOf { it.x }
        val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }
        val maxY = all.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY
        val side = max(max(width, height), 1f)
        val xInset = (side - width) / 2f
        val yInset = (side - height) / 2f
        return source.map { points ->
            points.map { point ->
                Point(
                    x = ((point.x - minX + xInset) / side) * CanvasSize,
                    y = ((point.y - minY + yInset) / side) * CanvasSize
                )
            }.let { normalizedPoints ->
                // A tap is a valid dot stroke. A tiny second point gives it a descriptor without
                // changing the user's visible stroke or requiring special repository entries.
                if (normalizedPoints.size == 1) {
                    normalizedPoints + normalizedPoints.first().copy(x = normalizedPoints.first().x + 1f)
                } else {
                    normalizedPoints
                }
            }
        }
    }

    private fun analyzeStroke(points: List<Point>): List<HandwritingSubStroke> {
        if (points.size < 2) return emptyList()
        val pivots = pivotIndices(points)
        return pivots.zipWithNext().mapNotNull { (startIndex, endIndex) ->
            val start = points[startIndex]
            val end = points[endIndex]
            val dx = end.x - start.x
            val dy = end.y - start.y
            val descriptorDirection = PI - atan2(start.y - end.y, start.x - end.x)
            val direction = (((descriptorDirection + 2.0 * PI) % (2.0 * PI)) * 256.0 / (2.0 * PI))
                .roundToInt() % 256
            val length = (hypot(dx, dy) / (CanvasSize * sqrt(2f)) * 255f)
                .roundToInt()
                .coerceIn(1, 255)
            HandwritingSubStroke(
                direction = direction,
                length = length,
                centerX = (((start.x + end.x) / 2f) / CanvasSize * 15f)
                    .roundToInt().coerceIn(0, 15),
                centerY = (((start.y + end.y) / 2f) / CanvasSize * 15f)
                    .roundToInt().coerceIn(0, 15)
            )
        }
    }

    private fun pivotIndices(points: List<Point>): List<Int> {
        if (points.size == 2) return listOf(0, 1)
        val markers = BooleanArray(points.size)
        markers[0] = true
        var previous = 0
        var first = 0
        var pivot = 1
        var localLength = distance(points[first], points[pivot])
        var runningLength = localLength
        for (nextIndex in 2 until points.size) {
            val pivotLength = distance(points[pivot], points[nextIndex])
            localLength += pivotLength
            runningLength += pivotLength
            val fromPrevious = distance(points[previous], points[nextIndex])
            val fromFirst = distance(points[first], points[nextIndex])
            if (localLength > MaxLocalLengthRatio * fromPrevious ||
                runningLength > MaxRunningLengthRatio * fromFirst
            ) {
                if (markers[previous] &&
                    distance(points[previous], points[pivot]) < MinSegmentLength
                ) {
                    markers[previous] = false
                }
                markers[pivot] = true
                runningLength = pivotLength
                first = pivot
            }
            localLength = pivotLength
            previous = pivot
            pivot = nextIndex
        }
        markers[pivot] = true
        if (previous != 0 && markers[previous] &&
            distance(points[previous], points[pivot]) < MinSegmentLength
        ) {
            markers[previous] = false
        }
        return markers.indices.filter(markers::get)
    }

    private fun distance(a: Point, b: Point): Float = hypot(a.x - b.x, a.y - b.y)

    private data class Point(val x: Float, val y: Float)
}

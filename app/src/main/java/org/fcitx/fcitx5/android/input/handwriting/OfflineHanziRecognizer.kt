/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.Context
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class OfflineHanziRecognizer(private val context: Context) {
    @Volatile
    private var repository: Repository? = null

    fun warmup() {
        repository()
    }

    fun recognize(
        strokes: List<HandwritingStroke>,
        limit: Int,
        cancelled: () -> Boolean = { false }
    ): List<HandwritingRecognition> {
        val input = HandwritingStrokeAnalyzer.analyze(strokes)
        if (input.subStrokes.isEmpty()) return emptyList()
        return repository().match(input, limit, cancelled)
    }

    private fun repository(): Repository {
        repository?.let { return it }
        return synchronized(this) {
            repository ?: loadRepository().also { repository = it }
        }
    }

    private fun loadRepository(): Repository = context.assets
        .open(AssetPath)
        .let(::BufferedInputStream)
        .let(::DataInputStream)
        .use { input ->
            require(input.readInt() == Magic) { "Unsupported handwriting repository" }
            val entryCount = input.readInt()
            val descriptorCount = input.readInt()
            val codePoints = IntArray(entryCount)
            val strokeCounts = ByteArray(entryCount)
            val descriptorCounts = ShortArray(entryCount)
            val descriptorStarts = IntArray(entryCount)
            repeat(entryCount) { index ->
                codePoints[index] = input.readInt()
                strokeCounts[index] = input.readUnsignedByte().toByte()
                descriptorCounts[index] = input.readUnsignedShort().toShort()
                descriptorStarts[index] = input.readInt()
            }
            val descriptors = ByteArray(descriptorCount * DescriptorSize)
            input.readFully(descriptors)
            Repository(
                codePoints,
                strokeCounts,
                descriptorCounts,
                descriptorStarts,
                descriptors
            )
        }

    private class Repository(
        private val codePoints: IntArray,
        private val strokeCounts: ByteArray,
        private val descriptorCounts: ShortArray,
        private val descriptorStarts: IntArray,
        private val descriptors: ByteArray
    ) {
        private val maxDescriptorCount = descriptorCounts.maxOf { it.toInt() and 0xffff }

        fun match(
            input: HandwritingStrokeAnalyzer.Result,
            limit: Int,
            cancelled: () -> Boolean
        ): List<HandwritingRecognition> {
            val keep = PriorityQueue<Scored>(compareByDescending(Scored::cost))
            val strokeTolerance = max(2, input.strokeCount / 3)
            val previous = FloatArray(maxDescriptorCount + 1)
            val current = FloatArray(maxDescriptorCount + 1)
            codePoints.indices.forEach { entryIndex ->
                // Rapid handwriting can supersede a CPU match before it finishes. Periodic
                // cancellation prevents obsolete characters from competing with the newest one.
                if (entryIndex and CancellationCheckMask == 0 && cancelled()) return emptyList()
                val strokeCount = strokeCounts[entryIndex].toInt() and 0xff
                if (abs(strokeCount - input.strokeCount) > strokeTolerance) return@forEach
                val count = descriptorCounts[entryIndex].toInt() and 0xffff
                if (count == 0 || abs(count - input.subStrokes.size) > max(4, input.subStrokes.size / 2)) {
                    return@forEach
                }
                val cost = matchCost(input.subStrokes, entryIndex, previous, current) +
                    abs(strokeCount - input.strokeCount) * StrokeCountPenalty
                if (keep.size < limit) {
                    keep += Scored(codePoints[entryIndex], cost)
                } else if (cost < requireNotNull(keep.peek()).cost) {
                    keep.poll()
                    keep += Scored(codePoints[entryIndex], cost)
                }
            }
            return keep.toList()
                .sortedBy(Scored::cost)
                .map { scored ->
                    HandwritingRecognition(
                        text = String(Character.toChars(scored.codePoint)),
                        score = 1f / (1f + scored.cost)
                    )
                }
        }

        private fun matchCost(
            input: List<HandwritingSubStroke>,
            entryIndex: Int,
            previousBuffer: FloatArray,
            currentBuffer: FloatArray
        ): Float {
            val repoCount = descriptorCounts[entryIndex].toInt() and 0xffff
            var previous = previousBuffer
            var current = currentBuffer
            for (index in 0..repoCount) previous[index] = index * GapPenalty
            input.forEachIndexed { inputIndex, inputStroke ->
                current[0] = (inputIndex + 1) * GapPenalty
                for (repoIndex in 1..repoCount) {
                    val base = (descriptorStarts[entryIndex] + repoIndex - 1) * DescriptorSize
                    val repoDirection = descriptors[base].toInt() and 0xff
                    val repoLength = descriptors[base + 1].toInt() and 0xff
                    val packedCenter = descriptors[base + 2].toInt() and 0xff
                    val substitution = previous[repoIndex - 1] + descriptorCost(
                        inputStroke,
                        repoDirection,
                        repoLength,
                        packedCenter ushr 4,
                        packedCenter and 0x0f
                    )
                    current[repoIndex] = min(
                        substitution,
                        min(previous[repoIndex] + GapPenalty, current[repoIndex - 1] + GapPenalty)
                    )
                }
                val swap = previous
                previous = current
                current = swap
            }
            return previous[repoCount] / max(input.size, repoCount).coerceAtLeast(1)
        }

        private fun descriptorCost(
            input: HandwritingSubStroke,
            direction: Int,
            length: Int,
            centerX: Int,
            centerY: Int
        ): Float {
            val rawDirectionDistance = abs(input.direction - direction)
            val directionDistance = min(rawDirectionDistance, 256 - rawDirectionDistance) / 128f
            val directionCost = if (directionDistance > 0.75f) {
                // Reversed strokes still retain shape information and should not dominate the
                // spatial match, especially for users who learned a different stroke direction.
                0.55f + (1f - directionDistance) * 0.2f
            } else {
                directionDistance
            }
            val lengthCost = abs(input.length - length) / 255f
            val centerCost = hypot(
                (input.centerX - centerX).toFloat(),
                (input.centerY - centerY).toFloat()
            ) / MaxCenterDistance
            return directionCost * 0.5f + lengthCost * 0.18f + centerCost * 0.32f
        }

        private data class Scored(val codePoint: Int, val cost: Float)
    }

    private companion object {
        const val AssetPath = "handwriting/mmah-substrokes-v1.bin"
        const val Magic = 0x48575231
        const val DescriptorSize = 3
        const val GapPenalty = 0.52f
        const val StrokeCountPenalty = 0.11f
        const val MaxCenterDistance = 21.213203f
        const val CancellationCheckMask = 0xff
    }
}

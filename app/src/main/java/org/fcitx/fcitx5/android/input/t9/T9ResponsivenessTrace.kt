/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import timber.log.Timber

object T9ResponsivenessTrace {
    const val SlowSectionThresholdNanos = 2_000_000L
    const val DefaultAggregationWindow = 20

    data class SectionSummary(
        val section: String,
        val count: Int,
        val slowCount: Int,
        val totalNanos: Long,
        val minNanos: Long,
        val maxNanos: Long
    ) {
        val averageNanos: Long
            get() = if (count == 0) 0 else totalNanos / count
    }

    private data class Config(
        val enabled: Boolean = false,
        val slowThresholdNanos: Long = SlowSectionThresholdNanos,
        val aggregationWindow: Int = DefaultAggregationWindow
    )

    private data class MutableSectionStats(
        var count: Int = 0,
        var slowCount: Int = 0,
        var totalNanos: Long = 0L,
        var minNanos: Long = Long.MAX_VALUE,
        var maxNanos: Long = 0L
    ) {
        fun add(elapsedNanos: Long, slowThresholdNanos: Long) {
            count += 1
            totalNanos += elapsedNanos
            minNanos = minOf(minNanos, elapsedNanos)
            maxNanos = maxOf(maxNanos, elapsedNanos)
            if (elapsedNanos >= slowThresholdNanos) slowCount += 1
        }

        fun toSummary(section: String): SectionSummary = SectionSummary(
            section = section,
            count = count,
            slowCount = slowCount,
            totalNanos = totalNanos,
            minNanos = minNanos.takeUnless { it == Long.MAX_VALUE } ?: 0L,
            maxNanos = maxNanos
        )
    }

    private val lock = Any()
    private val stats = mutableMapOf<String, MutableSectionStats>()

    @Volatile
    private var config = Config()

    fun configure(
        enabled: Boolean,
        slowThresholdNanos: Long = SlowSectionThresholdNanos,
        aggregationWindow: Int = DefaultAggregationWindow
    ) {
        synchronized(lock) {
            config = Config(
                enabled = enabled,
                slowThresholdNanos = slowThresholdNanos.coerceAtLeast(1L),
                aggregationWindow = aggregationWindow.coerceAtLeast(1)
            )
            stats.clear()
        }
    }

    fun reset() {
        synchronized(lock) {
            stats.clear()
        }
    }

    @PublishedApi
    internal fun enabled(): Boolean = config.enabled

    inline fun <T> measure(section: String, block: () -> T): T {
        if (!enabled()) return block()
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val elapsed = System.nanoTime() - start
            record(section, elapsed)?.let(::logSummary)
        }
    }

    @PublishedApi
    internal fun record(section: String, elapsedNanos: Long): SectionSummary? {
        val activeConfig = config
        if (!activeConfig.enabled) return null
        return synchronized(lock) {
            val sectionStats = stats.getOrPut(section) { MutableSectionStats() }
            sectionStats.add(elapsedNanos, activeConfig.slowThresholdNanos)
            if (sectionStats.count >= activeConfig.aggregationWindow) {
                sectionStats.toSummary(section).also {
                    stats.remove(section)
                }
            } else {
                null
            }
        }
    }

    @PublishedApi
    internal fun logSummary(summary: SectionSummary) {
        Timber.d(
            "T9 responsiveness: %s count=%d avg=%.2f ms min=%.2f ms max=%.2f ms slow=%d",
            summary.section,
            summary.count,
            summary.averageNanos / 1_000_000.0,
            summary.minNanos / 1_000_000.0,
            summary.maxNanos / 1_000_000.0,
            summary.slowCount
        )
    }
}

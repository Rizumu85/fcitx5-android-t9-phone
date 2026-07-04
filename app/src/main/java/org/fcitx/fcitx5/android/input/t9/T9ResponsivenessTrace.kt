/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
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

    @PublishedApi
    internal data class Config(
        val enabled: Boolean = false,
        val slowThresholdNanos: Long = SlowSectionThresholdNanos,
        val aggregationWindow: Int = DefaultAggregationWindow
    )

    private data class MutableSectionStats(
        var count: Int = 0,
        var slowCount: Int = 0,
        var totalNanos: Long = 0L,
        var minNanos: Long = Long.MAX_VALUE,
        var maxNanos: Long = 0L,
        var firstSlowSampleNanos: Long? = null
    ) {
        fun add(
            section: String,
            elapsedNanos: Long,
            slowThresholdNanos: Long,
            aggregationWindow: Int
        ): RecordResult? = synchronized(this) {
            count += 1
            totalNanos += elapsedNanos
            minNanos = minOf(minNanos, elapsedNanos)
            maxNanos = maxOf(maxNanos, elapsedNanos)
            if (elapsedNanos >= slowThresholdNanos) {
                slowCount += 1
                if (firstSlowSampleNanos == null) {
                    firstSlowSampleNanos = elapsedNanos
                }
            }
            if (count < aggregationWindow) {
                null
            } else {
                RecordResult(
                    summary = toSummary(section),
                    firstSlowSampleNanos = firstSlowSampleNanos
                ).also {
                    reset()
                }
            }
        }

        private fun reset() {
            count = 0
            slowCount = 0
            totalNanos = 0L
            minNanos = Long.MAX_VALUE
            maxNanos = 0L
            firstSlowSampleNanos = null
        }

        private fun toSummary(section: String): SectionSummary = SectionSummary(
            section = section,
            count = count,
            slowCount = slowCount,
            totalNanos = totalNanos,
            minNanos = minNanos.takeUnless { it == Long.MAX_VALUE } ?: 0L,
            maxNanos = maxNanos
        )
    }

    @PublishedApi
    internal data class RecordResult(
        val summary: SectionSummary,
        val firstSlowSampleNanos: Long?
    )

    private val summaryLock = Any()
    private val stats = ConcurrentHashMap<String, MutableSectionStats>()
    private val latestSummaries = linkedMapOf<String, SectionSummary>()
    private val logExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "T9ResponsivenessTrace").apply { isDaemon = true }
    }

    @Volatile
    @PublishedApi
    internal var config = Config()

    fun configure(
        enabled: Boolean,
        slowThresholdNanos: Long = SlowSectionThresholdNanos,
        aggregationWindow: Int = DefaultAggregationWindow
    ) {
        config = Config(
            enabled = enabled,
            slowThresholdNanos = slowThresholdNanos.coerceAtLeast(1L),
            aggregationWindow = aggregationWindow.coerceAtLeast(1)
        )
        stats.clear()
        synchronized(summaryLock) {
            latestSummaries.clear()
        }
    }

    fun reset() {
        stats.clear()
        synchronized(summaryLock) {
            latestSummaries.clear()
        }
    }

    fun latestSummaries(): List<SectionSummary> =
        synchronized(summaryLock) {
            latestSummaries.values.toList()
        }

    @PublishedApi
    internal fun enabled(): Boolean = config.enabled

    @PublishedApi
    internal fun slowThresholdNanos(): Long = config.slowThresholdNanos

    inline fun <T> measure(section: String, block: () -> T): T {
        val activeConfig = config
        if (!activeConfig.enabled) return block()
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val elapsed = System.nanoTime() - start
            recordForMeasure(section, elapsed, activeConfig)?.let { result ->
                result.firstSlowSampleNanos?.let { logSlowSample(section, it) }
                logSummary(result.summary)
            }
        }
    }

    internal fun record(section: String, elapsedNanos: Long): SectionSummary? =
        recordForMeasure(section, elapsedNanos, config)?.summary

    @PublishedApi
    internal fun recordForMeasure(section: String, elapsedNanos: Long, activeConfig: Config): RecordResult? {
        if (!activeConfig.enabled) return null
        val result = stats.getOrPut(section) { MutableSectionStats() }.add(
            section = section,
            elapsedNanos = elapsedNanos,
            slowThresholdNanos = activeConfig.slowThresholdNanos,
            aggregationWindow = activeConfig.aggregationWindow
        ) ?: return null
        synchronized(summaryLock) {
            latestSummaries[result.summary.section] = result.summary
            while (latestSummaries.size > MAX_LATEST_SUMMARIES) {
                val first = latestSummaries.keys.firstOrNull() ?: break
                latestSummaries.remove(first)
            }
        }
        return result
    }

    @PublishedApi
    internal fun logSummary(summary: SectionSummary) {
        logExecutor.execute {
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

    @PublishedApi
    internal fun logSlowSample(section: String, elapsedNanos: Long) {
        logExecutor.execute {
            Timber.d(
                "T9 responsiveness slow sample: %s took %.2f ms",
                section,
                elapsedNanos / 1_000_000.0
            )
        }
    }

    private const val MAX_LATEST_SUMMARIES = 96
}

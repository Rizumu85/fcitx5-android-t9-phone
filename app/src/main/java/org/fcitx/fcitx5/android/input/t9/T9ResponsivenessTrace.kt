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

    data class InputLatencySummary(
        val path: String,
        val count: Int,
        val replacedCount: Int,
        val averageNanos: Long,
        val p50Nanos: Long,
        val p95Nanos: Long,
        val maxNanos: Long,
        val averageDecisionNanos: Long,
        val averageSourceWaitNanos: Long,
        val averageSnapshotNanos: Long,
        val averageRenderNanos: Long,
        val averageFrameWaitNanos: Long
    )

    private data class Config(
        val enabled: Boolean = false,
        val detailedSections: Boolean = false,
        val slowThresholdNanos: Long = SlowSectionThresholdNanos,
        val aggregationWindow: Int = DefaultAggregationWindow,
        val nanoTime: () -> Long = System::nanoTime
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

    private data class InputTransaction(
        val id: Long,
        val path: String,
        val startedNanos: Long,
        val requiresSourceEvent: Boolean,
        var decisionCompleteNanos: Long? = null,
        var sourceEventNanos: Long? = null,
        var snapshotReadyNanos: Long? = null,
        var renderCompleteNanos: Long? = null
    )

    private data class InputLatencySample(
        val totalNanos: Long,
        val decisionNanos: Long,
        val sourceWaitNanos: Long,
        val snapshotNanos: Long,
        val renderNanos: Long,
        val frameWaitNanos: Long
    )

    private data class MutableInputLatencyStats(
        val samples: MutableList<InputLatencySample> = mutableListOf(),
        var replacedCount: Int = 0
    )

    private val lock = Any()
    private val stats = mutableMapOf<String, MutableSectionStats>()
    private val latestSummaries = linkedMapOf<String, SectionSummary>()
    private val inputStats = mutableMapOf<String, MutableInputLatencyStats>()
    private val latestCompletedInputSummaries = linkedMapOf<String, InputLatencySummary>()
    private var nextInputId = 1L
    private var activeInput: InputTransaction? = null

    @Volatile
    private var config = Config()

    fun configure(
        enabled: Boolean,
        slowThresholdNanos: Long = SlowSectionThresholdNanos,
        aggregationWindow: Int = DefaultAggregationWindow,
        detailedSections: Boolean = false,
        nanoTime: () -> Long = System::nanoTime
    ) {
        synchronized(lock) {
            config = Config(
                enabled = enabled,
                detailedSections = detailedSections,
                slowThresholdNanos = slowThresholdNanos.coerceAtLeast(1L),
                aggregationWindow = aggregationWindow.coerceAtLeast(1),
                nanoTime = nanoTime
            )
            clearLocked()
        }
    }

    fun reset() {
        synchronized(lock) {
            clearLocked()
        }
    }

    fun latestSummaries(): List<SectionSummary> =
        synchronized(lock) {
            latestSummaries.values.toList()
        }

    fun latestInputSummaries(): List<InputLatencySummary> =
        synchronized(lock) {
            buildMap {
                putAll(latestCompletedInputSummaries)
                inputStats.forEach { (path, pathStats) ->
                    // The developer report must remain useful before a full log window fills.
                    if (pathStats.samples.isNotEmpty()) {
                        put(path, pathStats.toSummary(path))
                    }
                }
            }.values.toList()
        }

    fun beginInput(path: String, requiresSourceEvent: Boolean = false): Long? {
        val activeConfig = config
        if (!activeConfig.enabled) return null
        return synchronized(lock) {
            activeInput?.let { previous ->
                inputStats.getOrPut(previous.path, ::MutableInputLatencyStats).replacedCount += 1
            }
            nextInputId++.also { id ->
                activeInput = InputTransaction(
                    id = id,
                    path = path,
                    startedNanos = activeConfig.nanoTime(),
                    requiresSourceEvent = requiresSourceEvent
                )
            }
        }
    }

    fun markDecisionComplete(inputId: Long?) {
        mark(inputId) { transaction, now ->
            if (transaction.decisionCompleteNanos == null) {
                transaction.decisionCompleteNanos = now
            }
        }
    }

    fun markSourceEvent() {
        val activeConfig = config
        if (!activeConfig.enabled) return
        synchronized(lock) {
            val transaction = activeInput ?: return
            if (!transaction.requiresSourceEvent) return
            if (transaction.sourceEventNanos == null) {
                transaction.sourceEventNanos = activeConfig.nanoTime()
            }
        }
    }

    fun activeInputId(): Long? =
        if (!config.enabled) null else synchronized(lock) { activeInput?.id }

    fun markSnapshotReady(inputId: Long?) {
        mark(inputId) { transaction, now ->
            // Engine-backed input must not let an unrelated pending UI pass claim its frame.
            if ((!transaction.requiresSourceEvent || transaction.sourceEventNanos != null) &&
                transaction.snapshotReadyNanos == null
            ) {
                transaction.snapshotReadyNanos = now
            }
        }
    }

    fun markRenderComplete(inputId: Long?) {
        mark(inputId) { transaction, now ->
            if (transaction.snapshotReadyNanos != null && transaction.renderCompleteNanos == null) {
                transaction.renderCompleteNanos = now
            }
        }
    }

    fun completeFrame(inputId: Long?): InputLatencySummary? {
        if (inputId == null) return null
        val activeConfig = config
        if (!activeConfig.enabled) return null
        val summary = synchronized(lock) {
            val transaction = activeInput?.takeIf { it.id == inputId } ?: return null
            if (transaction.requiresSourceEvent && transaction.sourceEventNanos == null) return null
            if (transaction.snapshotReadyNanos == null || transaction.renderCompleteNanos == null) return null
            val frameNanos = activeConfig.nanoTime()
            val sample = transaction.toSample(frameNanos)
            activeInput = null
            val pathStats = inputStats.getOrPut(transaction.path, ::MutableInputLatencyStats)
            pathStats.samples += sample
            if (pathStats.samples.size < activeConfig.aggregationWindow) return null
            pathStats.toSummary(transaction.path).also {
                latestCompletedInputSummaries[transaction.path] = it
                pathStats.samples.clear()
                pathStats.replacedCount = 0
            }
        }
        logInputSummary(summary)
        return summary
    }

    fun cancelActiveInput() {
        if (!config.enabled) return
        synchronized(lock) {
            activeInput = null
        }
    }

    @PublishedApi
    internal fun detailedSectionsEnabled(): Boolean =
        config.enabled && config.detailedSections

    @PublishedApi
    internal fun slowThresholdNanos(): Long = config.slowThresholdNanos

    @PublishedApi
    internal fun nanoTime(): Long = config.nanoTime()

    inline fun <T> measure(section: String, block: () -> T): T {
        if (!detailedSectionsEnabled()) return block()
        val start = nanoTime()
        return try {
            block()
        } finally {
            val elapsed = nanoTime() - start
            if (elapsed >= slowThresholdNanos()) {
                logSlowSample(section, elapsed)
            }
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
                    latestSummaries[section] = it
                }
            } else {
                null
            }
        }
    }

    private inline fun mark(
        inputId: Long?,
        update: (InputTransaction, Long) -> Unit
    ) {
        if (inputId == null) return
        val activeConfig = config
        if (!activeConfig.enabled) return
        synchronized(lock) {
            val transaction = activeInput?.takeIf { it.id == inputId } ?: return
            update(transaction, activeConfig.nanoTime())
        }
    }

    private fun InputTransaction.toSample(frameNanos: Long): InputLatencySample {
        val source = sourceEventNanos
        // Fcitx may publish its source event synchronously before command execution returns.
        // Clamp the stage boundary to preserve an additive, non-overlapping latency breakdown.
        val decision = minOf(decisionCompleteNanos ?: startedNanos, source ?: Long.MAX_VALUE)
        val sourceBoundary = maxOf(source ?: decision, decision)
        val snapshot = maxOf(requireNotNull(snapshotReadyNanos), sourceBoundary)
        val render = maxOf(requireNotNull(renderCompleteNanos), snapshot)
        return InputLatencySample(
            totalNanos = (frameNanos - startedNanos).coerceAtLeast(0L),
            decisionNanos = (decision - startedNanos).coerceAtLeast(0L),
            sourceWaitNanos = if (source == null) 0L else sourceBoundary - decision,
            snapshotNanos = snapshot - sourceBoundary,
            renderNanos = (render - snapshot).coerceAtLeast(0L),
            frameWaitNanos = (frameNanos - render).coerceAtLeast(0L)
        )
    }

    private fun MutableInputLatencyStats.toSummary(path: String): InputLatencySummary {
        val totals = samples.map(InputLatencySample::totalNanos).sorted()
        return InputLatencySummary(
            path = path,
            count = samples.size,
            replacedCount = replacedCount,
            averageNanos = samples.averageOf(InputLatencySample::totalNanos),
            p50Nanos = totals.percentile(50),
            p95Nanos = totals.percentile(95),
            maxNanos = totals.lastOrNull() ?: 0L,
            averageDecisionNanos = samples.averageOf(InputLatencySample::decisionNanos),
            averageSourceWaitNanos = samples.averageOf(InputLatencySample::sourceWaitNanos),
            averageSnapshotNanos = samples.averageOf(InputLatencySample::snapshotNanos),
            averageRenderNanos = samples.averageOf(InputLatencySample::renderNanos),
            averageFrameWaitNanos = samples.averageOf(InputLatencySample::frameWaitNanos)
        )
    }

    private fun List<Long>.percentile(percent: Int): Long {
        if (isEmpty()) return 0L
        val index = (((size * percent) + 99) / 100 - 1).coerceIn(indices)
        return this[index]
    }

    private inline fun List<InputLatencySample>.averageOf(
        value: (InputLatencySample) -> Long
    ): Long = if (isEmpty()) 0L else sumOf(value) / size

    private fun clearLocked() {
        stats.clear()
        latestSummaries.clear()
        inputStats.clear()
        latestCompletedInputSummaries.clear()
        activeInput = null
        nextInputId = 1L
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

    private fun logInputSummary(summary: InputLatencySummary) {
        Timber.d(
            "T9 input latency: path=%s count=%d replaced=%d avg=%.2f ms p50=%.2f ms p95=%.2f ms max=%.2f ms stages[decision=%.2f source=%.2f snapshot=%.2f render=%.2f frame=%.2f]",
            summary.path,
            summary.count,
            summary.replacedCount,
            summary.averageNanos / 1_000_000.0,
            summary.p50Nanos / 1_000_000.0,
            summary.p95Nanos / 1_000_000.0,
            summary.maxNanos / 1_000_000.0,
            summary.averageDecisionNanos / 1_000_000.0,
            summary.averageSourceWaitNanos / 1_000_000.0,
            summary.averageSnapshotNanos / 1_000_000.0,
            summary.averageRenderNanos / 1_000_000.0,
            summary.averageFrameWaitNanos / 1_000_000.0
        )
    }

    @PublishedApi
    internal fun logSlowSample(section: String, elapsedNanos: Long) {
        Timber.d(
            "T9 responsiveness slow sample: %s took %.2f ms",
            section,
            elapsedNanos / 1_000_000.0
        )
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core.performance

import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.Trace
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

object StartupPerformanceTrace {
    enum class Stage(val traceName: String) {
        APPLICATION_CREATE("application-create"),
        APPLICATION_DIRECT_BOOT("application-direct-boot"),
        APPLICATION_CRASH_HANDLER("application-crash-handler"),
        APPLICATION_PREFS_LOGGING("application-prefs-logging"),
        APPLICATION_RUNTIME_MANAGERS("application-runtime-managers"),
        APPLICATION_CLIPBOARD_INIT("application-clipboard-init"),
        APPLICATION_THEME_INIT("application-theme-init"),
        APPLICATION_LOCALES_INIT("application-locales-init"),
        APPLICATION_RECEIVER_REGISTRATION("application-receiver-registration"),
        APPLICATION_DEVICE_STORAGE_SYNC("application-device-storage-sync"),
        APPLICATION_RESTART_RECEIVER("application-restart-receiver"),
        DATA_INSTALLATION("data-installation"),
        FCITX_NATIVE_STARTUP("fcitx-native-startup"),
        INPUT_VIEW_CONSTRUCTION("input-view-construction"),
        NAVBAR_EVALUATION("navbar-evaluation"),
        INPUT_VIEW_CREATE("input-view-create"),
        INPUT_VIEW_ATTACH("input-view-attach"),
        CANDIDATE_VIEW_CREATE("candidate-view-create"),
        CANDIDATE_VIEW_ATTACH("candidate-view-attach"),
        MODE_INDICATOR_REPLACE("mode-indicator-replace"),
        INPUT_SCOPE_SETUP("input-scope-setup"),
        INPUT_SCOPE_REGISTRATION("input-scope-registration"),
        INPUT_SCOPE_READY("input-scope-ready"),
        INPUT_AUXILIARY_SURFACES_CREATE("input-auxiliary-surfaces-create"),
        INPUT_CHROME_PRIMITIVES_CREATE("input-chrome-primitives-create"),
        INPUT_SELECTION_PANEL_CREATE("input-selection-panel-create"),
        INPUT_NUMBER_PANEL_CREATE("input-number-panel-create"),
        INPUT_PASSWORD_PREVIEW_CREATE("input-password-preview-create"),
        INPUT_COMPONENTS_CREATE("input-components-create"),
        INPUT_PREFERENCE_BINDINGS("input-preference-bindings"),
        ACTIVE_KEYBOARD_CREATE("active-keyboard-create"),
        ACTIVE_KEYBOARD_ATTACH("active-keyboard-attach"),
        INPUT_CHROME_CREATE("input-chrome-create"),
        INPUT_TREE_ASSEMBLY("input-tree-assembly"),
        DATA_MAIN_FINGERPRINT_LOAD("data-main-fingerprint-load"),
        DATA_PLUGIN_DISCOVERY("data-plugin-discovery"),
        DATA_MERGED_FINGERPRINT_LOAD("data-merged-fingerprint-load"),
        DATA_INSTALLATION_STATE_LOAD("data-installation-state-load"),
        DATA_INSTALLATION_STATE_READ("data-installation-state-read"),
        DATA_INSTALLATION_STATE_DECODE("data-installation-state-decode"),
        DATA_COMPLETION("data-completion")
    }

    enum class Milestone(val traceName: String) {
        APPLICATION_CREATED("application-created"),
        FCITX_START_REQUESTED("fcitx-start-requested"),
        FCITX_READY("fcitx-ready"),
        RIME_READY("rime-ready"),
        INPUT_VIEW_REQUESTED("input-view-requested"),
        INPUT_VIEW_CREATED("input-view-created"),
        FIRST_INPUT_SURFACE_FRAME("first-input-surface-frame")
    }

    data class StageTiming(
        val stage: Stage,
        val startOffsetNanos: Long,
        val durationNanos: Long
    )

    data class MilestoneTiming(
        val milestone: Milestone,
        val offsetNanos: Long
    )

    data class Snapshot(
        val generation: Long,
        val capturedOffsetNanos: Long,
        val stages: List<StageTiming>,
        val milestones: List<MilestoneTiming>,
        val complete: Boolean
    ) {
        fun stage(stage: Stage): StageTiming? = stages.firstOrNull { it.stage == stage }

        fun milestone(milestone: Milestone): MilestoneTiming? =
            milestones.firstOrNull { it.milestone == milestone }
    }

    class StageToken internal constructor(
        internal val generation: Long,
        internal val stage: Stage,
        internal val startedNanos: Long,
        internal val traceThread: Thread,
        internal val androidTracing: Boolean
    )

    private data class Transaction(
        val generation: Long,
        val processStartNanos: Long,
        val nanoTime: () -> Long,
        val androidTracing: Boolean,
        val startedStages: MutableSet<Stage> = mutableSetOf(),
        val stages: MutableMap<Stage, StageTiming> = linkedMapOf(),
        val milestones: MutableMap<Milestone, MilestoneTiming> = linkedMapOf(),
        var firstSurfacePublished: Boolean = false,
        var completePublished: Boolean = false
    )

    private data class Publication(
        val label: String,
        val snapshot: Snapshot
    )

    private val lock = Any()

    @Volatile
    private var enabled = false

    private var transaction: Transaction? = null

    fun startProcess() {
        val now = SystemClock.elapsedRealtimeNanos()
        val processStart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TimeUnit.MILLISECONDS.toNanos(Process.getStartElapsedRealtime()).coerceAtMost(now)
        } else {
            now
        }
        start(
            generation = Process.myPid().toLong(),
            processStartNanos = processStart,
            nanoTime = SystemClock::elapsedRealtimeNanos,
            androidTracing = true
        )
    }

    fun configure(enabled: Boolean) {
        this.enabled = enabled
    }

    fun beginStage(stage: Stage): StageToken? {
        val token = synchronized(lock) {
            val current = transaction ?: return null
            if (!current.startedStages.add(stage)) return null
            StageToken(
                generation = current.generation,
                stage = stage,
                startedNanos = current.nanoTime(),
                traceThread = Thread.currentThread(),
                androidTracing = current.androidTracing
            )
        }
        if (token.androidTracing) {
            Trace.beginSection("FcitxStartup:${stage.traceName}")
        }
        return token
    }

    fun endStage(token: StageToken?) {
        if (token == null) return
        if (token.androidTracing && token.traceThread === Thread.currentThread()) {
            Trace.endSection()
        }
        synchronized(lock) {
            val current = transaction ?: return
            if (current.generation != token.generation || token.stage in current.stages) return
            val endedNanos = current.nanoTime()
            current.stages[token.stage] = StageTiming(
                stage = token.stage,
                startOffsetNanos = (token.startedNanos - current.processStartNanos).coerceAtLeast(0L),
                durationNanos = (endedNanos - token.startedNanos).coerceAtLeast(0L)
            )
        }
    }

    fun <T> measure(stage: Stage, block: () -> T): T {
        val token = beginStage(stage)
        return try {
            block()
        } finally {
            endStage(token)
        }
    }

    fun mark(milestone: Milestone) {
        var publication: Publication? = null
        var androidTracing = false
        synchronized(lock) {
            val current = transaction ?: return
            if (milestone in current.milestones) return
            current.milestones[milestone] = MilestoneTiming(
                milestone = milestone,
                offsetNanos = (current.nanoTime() - current.processStartNanos).coerceAtLeast(0L)
            )
            androidTracing = current.androidTracing
            publication = publicationLocked(current)
        }
        if (androidTracing) {
            Trace.beginSection("FcitxStartup:${milestone.traceName}")
            Trace.endSection()
        }
        publication?.let(::logPublication)
    }

    fun latestSnapshot(): Snapshot? = synchronized(lock) {
        if (!enabled) return null
        transaction?.toSnapshot()
    }

    internal fun startForTest(
        generation: Long,
        processStartNanos: Long,
        enabled: Boolean,
        nanoTime: () -> Long
    ) {
        this.enabled = enabled
        start(generation, processStartNanos, nanoTime, androidTracing = false)
    }

    internal fun resetForTest() {
        synchronized(lock) {
            transaction = null
            enabled = false
        }
    }

    private fun start(
        generation: Long,
        processStartNanos: Long,
        nanoTime: () -> Long,
        androidTracing: Boolean
    ) {
        synchronized(lock) {
            if (transaction?.generation == generation) return
            transaction = Transaction(
                generation = generation,
                processStartNanos = processStartNanos,
                nanoTime = nanoTime,
                androidTracing = androidTracing
            )
        }
    }

    private fun publicationLocked(current: Transaction): Publication? {
        if (!enabled || Milestone.FIRST_INPUT_SURFACE_FRAME !in current.milestones) return null
        val snapshot = current.toSnapshot()
        if (snapshot.complete && !current.completePublished) {
            current.firstSurfacePublished = true
            current.completePublished = true
            return Publication("complete", snapshot)
        }
        if (!current.firstSurfacePublished) {
            current.firstSurfacePublished = true
            return Publication("first-surface", snapshot)
        }
        return null
    }

    private fun Transaction.toSnapshot(): Snapshot {
        val capturedOffset = maxOf(
            stages.values.maxOfOrNull { it.startOffsetNanos + it.durationNanos } ?: 0L,
            milestones.values.maxOfOrNull(MilestoneTiming::offsetNanos) ?: 0L
        )
        val required = setOf(
            Milestone.APPLICATION_CREATED,
            Milestone.FCITX_READY,
            Milestone.RIME_READY,
            Milestone.INPUT_VIEW_CREATED,
            Milestone.FIRST_INPUT_SURFACE_FRAME
        )
        return Snapshot(
            generation = generation,
            capturedOffsetNanos = capturedOffset,
            stages = Stage.entries.mapNotNull(stages::get),
            milestones = Milestone.entries.mapNotNull(milestones::get),
            complete = milestones.keys.containsAll(required)
        )
    }

    private fun logPublication(publication: Publication) {
        val snapshot = publication.snapshot
        val stageReport = snapshot.stages.joinToString(separator = ", ") {
            "${it.stage.traceName}=${formatMillis(it.durationNanos)}"
        }
        val milestoneReport = snapshot.milestones.joinToString(separator = ", ") {
            "${it.milestone.traceName}=${formatMillis(it.offsetNanos)}"
        }
        Timber.i(
            "Startup performance [%s]: elapsed=%s; stages={%s}; milestones={%s}",
            publication.label,
            formatMillis(snapshot.capturedOffsetNanos),
            stageReport,
            milestoneReport
        )
    }

    private fun formatMillis(nanos: Long): String =
        String.format(Locale.US, "%.2f ms", nanos / 1_000_000.0)
}

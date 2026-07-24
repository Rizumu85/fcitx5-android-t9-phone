/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.os.Bundle
import android.os.Debug
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.data.DataManager
import org.fcitx.fcitx5.android.core.performance.StartupPerformanceTrace
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.clipboard.ClipboardManager
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.t9.T9ResponsivenessTrace
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.modified.MySwitchPreference
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.iso8601UTCDateTime
import org.fcitx.fcitx5.android.utils.setupForest
import org.fcitx.fcitx5.android.utils.startActivity
import org.fcitx.fcitx5.android.utils.toast
import timber.log.Timber
import java.io.File
import java.util.Locale

class DeveloperFragment : PaddingPreferenceFragment() {

    private lateinit var hprofFile: File
    private lateinit var launcher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher = registerForActivityResult(CreateDocument("application/octet-stream")) { uri ->
            if (uri == null) {
                hprofFile.delete()
                return@registerForActivityResult
            }
            val ctx = requireContext()
            lifecycleScope.launch(NonCancellable + Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)!!.use { o ->
                        hprofFile.inputStream().use { i -> i.copyTo(o) }
                    }
                }.let { ctx.toast(it) }
                hprofFile.delete()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addPreference(R.string.real_time_logs) {
                startActivity<LogActivity>()
            }
            addPreference(MySwitchPreference(context).apply {
                key = AppPrefs.getInstance().internal.verboseLog.key
                setTitle(R.string.verbose_log)
                setDefaultValue(false)
                isIconSpaceReserved = false
                isSingleLineTitle = false
                setOnPreferenceChangeListener { _, newValue ->
                    val verbose = (newValue as? Boolean) == true
                    Timber.setupForest(verbose)
                    FcitxDaemon.getFirstConnectionOrNull()?.runIfReady {
                        setLogRule(verbose)
                    }
                    true
                }
            })
            addPreference(MySwitchPreference(context).apply {
                key = AppPrefs.getInstance().internal.editorInfoInspector.key
                setTitle(R.string.editor_info_inspector)
                setDefaultValue(false)
                isIconSpaceReserved = false
                isSingleLineTitle = false
            })
            addPreference(MySwitchPreference(context).apply {
                key = AppPrefs.getInstance().internal.t9ResponsivenessTrace.key
                setTitle(R.string.t9_responsiveness_trace)
                setSummary(R.string.t9_responsiveness_trace_summary)
                setDefaultValue(false)
                isIconSpaceReserved = false
                isSingleLineTitle = false
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = (newValue as? Boolean) == true
                    T9ResponsivenessTrace.configure(enabled = enabled)
                    StartupPerformanceTrace.configure(enabled = enabled)
                    true
                }
            })
            addPreference(R.string.t9_responsiveness_trace_report) {
                AlertDialog.Builder(context)
                    .setTitle(R.string.t9_responsiveness_trace_report)
                    .setMessage(formatT9ResponsivenessReport())
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            addPreference(R.string.restart_fcitx_instance) {
                AlertDialog.Builder(context)
                    .setTitle(R.string.restart_fcitx_instance)
                    .setMessage(R.string.restart_fcitx_instance_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            withContext(NonCancellable + Dispatchers.IO) {
                                FcitxDaemon.restartFcitx()
                                withContext(Dispatchers.Main) {
                                    context.toast(R.string.done)
                                }
                            }
                        }
                    }
                    .show()
            }
            addPreference(R.string.delete_and_sync_data) {
                AlertDialog.Builder(context)
                    .setTitle(R.string.delete_and_sync_data)
                    .setMessage(R.string.delete_and_sync_data_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch(NonCancellable + Dispatchers.IO) {
                            DataManager.deleteAndSync()
                            withContext(Dispatchers.Main) {
                                context.toast(R.string.synced)
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            addPreference(R.string.clear_clb_db) {
                AlertDialog.Builder(context)
                    .setTitle(R.string.clear_clb_db)
                    .setMessage(R.string.clear_clp_db_confirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch(NonCancellable + Dispatchers.IO) {
                            ClipboardManager.nukeTable()
                            withContext(Dispatchers.Main) {
                                context.toast(R.string.done)
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            addPreference(R.string.capture_heap_dump) {
                val fileName = "${context.packageName}_${iso8601UTCDateTime()}.hprof"
                hprofFile = context.cacheDir.resolve(fileName)
                System.gc()
                Debug.dumpHprofData(hprofFile.absolutePath)
                launcher.launch(fileName)
            }
        }
    }

    private fun formatT9ResponsivenessReport(): String {
        val startupSnapshot = StartupPerformanceTrace.latestSnapshot()
        val inputSummaries = T9ResponsivenessTrace.latestInputSummaries()
            .sortedByDescending { it.p95Nanos }
        val sectionSummaries = T9ResponsivenessTrace.latestSummaries()
            .sortedByDescending { it.averageNanos }
        if (startupSnapshot == null && inputSummaries.isEmpty() && sectionSummaries.isEmpty()) {
            return getString(R.string.t9_responsiveness_trace_report_empty)
        }
        val inputReport = inputSummaries.joinToString(separator = "\n\n") { summary ->
            String.format(
                Locale.US,
                "%s: avg %.2f ms, p50 %.2f ms, p95 %.2f ms, max %.2f ms, replaced %d/%d\n" +
                    "stages avg: decision %.2f, effect %.2f, source %.2f " +
                    "(queue %.2f, engine %.2f, callback %.2f), snapshot %.2f, " +
                    "render %.2f, frame %.2f ms",
                summary.path,
                summary.averageNanos / 1_000_000.0,
                summary.p50Nanos / 1_000_000.0,
                summary.p95Nanos / 1_000_000.0,
                summary.maxNanos / 1_000_000.0,
                summary.replacedCount,
                summary.replacedCount + summary.count,
                summary.averageDecisionNanos / 1_000_000.0,
                summary.averageEffectNanos / 1_000_000.0,
                summary.averageSourceWaitNanos / 1_000_000.0,
                summary.averageEngineQueueNanos / 1_000_000.0,
                summary.averageEngineDispatchNanos / 1_000_000.0,
                summary.averageSourceCallbackNanos / 1_000_000.0,
                summary.averageSnapshotNanos / 1_000_000.0,
                summary.averageRenderNanos / 1_000_000.0,
                summary.averageFrameWaitNanos / 1_000_000.0
            )
        }
        val sectionReport = sectionSummaries.joinToString(separator = "\n") { summary ->
            String.format(
                Locale.US,
                "%s: avg %.2f ms, max %.2f ms, slow %d/%d",
                summary.section,
                summary.averageNanos / 1_000_000.0,
                summary.maxNanos / 1_000_000.0,
                summary.slowCount,
                summary.count
            )
        }
        val startupReport = startupSnapshot?.let { snapshot ->
            val stages = snapshot.stages.joinToString(separator = ", ") {
                String.format(
                    Locale.US,
                    "%s %.2f ms",
                    it.stage.traceName,
                    it.durationNanos / 1_000_000.0
                )
            }
            val milestones = snapshot.milestones.joinToString(separator = ", ") {
                String.format(
                    Locale.US,
                    "%s %.2f ms",
                    it.milestone.traceName,
                    it.offsetNanos / 1_000_000.0
                )
            }
            String.format(
                Locale.US,
                "Startup (%s): elapsed %.2f ms\nstages: %s\nmilestones: %s",
                if (snapshot.complete) "complete" else "partial",
                snapshot.capturedOffsetNanos / 1_000_000.0,
                stages,
                milestones
            )
        }.orEmpty()
        return listOf(startupReport, inputReport, sectionReport)
            .filter(String::isNotEmpty)
            .joinToString(separator = "\n\n")
    }

}

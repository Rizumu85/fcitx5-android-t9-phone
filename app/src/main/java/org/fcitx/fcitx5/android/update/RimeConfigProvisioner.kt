/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object RimeConfigProvisioningPolicy {
    enum class Decision {
        READY,
        WAIT,
        DOWNLOAD
    }

    fun decide(
        installedVersion: String?,
        requiredVersion: String,
        healthy: Boolean,
        matchingDownloadPending: Boolean
    ): Decision = when {
        healthy && installedVersion != null &&
            UpdateVersion.compare(installedVersion, requiredVersion) >= 0 -> Decision.READY
        matchingDownloadPending -> Decision.WAIT
        else -> Decision.DOWNLOAD
    }
}

object RimeConfigProvisioner {
    private const val ArchiveName = "rime-ice-t9-phone-main.zip"
    private const val ReleasePage =
        "https://github.com/Rizumu85/rime-ice-t9-phone/releases"
    private val running = AtomicBoolean()
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "RimeConfigProvisioning").apply { isDaemon = true }
    }
    private val PollDelaysSeconds = longArrayOf(0, 2, 4, 8, 15, 30, 60)

    fun ensureRequiredConfig(context: Context) {
        val appContext = context.applicationContext
        executor.execute {
            runCatching { ensureRequiredConfigInternal(appContext) }
                .onFailure { Timber.w(it, "Unable to schedule required Rime configuration") }
        }
    }

    private fun ensureRequiredConfigInternal(context: Context) {
        if (BuildConfig.PERFORMANCE_HARNESS) return
        val version = BuildConfig.RIME_CONFIG_BASELINE_VERSION
        val installedVersion = RimeConfigVersionStore.currentVersion(context)
        val healthy = RimeConfigVersionStore.isHealthy(context)
        val preliminaryDecision = RimeConfigProvisioningPolicy.decide(
            installedVersion = installedVersion,
            requiredVersion = version,
            healthy = healthy,
            matchingDownloadPending = false
        )
        if (preliminaryDecision == RimeConfigProvisioningPolicy.Decision.READY) return
        // A disabled or vendor-broken DownloadManager must not strand provisioning before the
        // direct-transfer fallback, so its failures route straight to that fallback.
        val downloadManagerPending = runCatching {
            UpdateDownloader.hasPending(context, UpdateComponent.RIME_CONFIG, version)
        }.onFailure {
            Timber.w(it, "Unable to query DownloadManager for Rime configuration")
        }
        val decision = RimeConfigProvisioningPolicy.decide(
            installedVersion = installedVersion,
            requiredVersion = version,
            healthy = healthy,
            matchingDownloadPending = downloadManagerPending.getOrDefault(false)
        )

        if (!running.compareAndSet(false, true)) return
        try {
            val downloadManagerUsable = when {
                downloadManagerPending.isFailure -> false
                decision != RimeConfigProvisioningPolicy.Decision.DOWNLOAD -> true
                else -> runCatching {
                    UpdateDownloader.enqueue(
                        context = context,
                        artifact = requiredArtifact(),
                        options = UpdateDownloader.Options(
                            automatic = true,
                            expectedSha256 = BuildConfig.RIME_CONFIG_BASELINE_SHA256
                        )
                    )
                }.onFailure {
                    Timber.w(it, "Unable to enqueue Rime configuration download")
                }.getOrDefault(false)
            }
            if (downloadManagerUsable) {
                schedulePoll(context, attempt = 0)
            } else {
                schedulePoll(context, attempt = PollDelaysSeconds.lastIndex, delaySeconds = 0L)
            }
        } catch (error: Throwable) {
            running.set(false)
            throw error
        }
    }

    private fun schedulePoll(
        context: Context,
        attempt: Int,
        delaySeconds: Long = PollDelaysSeconds[attempt]
    ) {
        executor.schedule(
            {
                val finished = try {
                    poll(context, attempt)
                } catch (error: Throwable) {
                    // The scheduled executor would swallow this silently; leaving `running` set
                    // would then disable provisioning for the rest of the process.
                    Timber.w(error, "Rime configuration provisioning failed")
                    true
                }
                if (finished) running.set(false)
            },
            delaySeconds,
            TimeUnit.SECONDS
        )
    }

    /** Returns false only when a later attempt has been scheduled. */
    private fun poll(context: Context, attempt: Int): Boolean {
        if (isReady(context)) {
            cancelAutomaticDownload(context)
            return true
        }
        val archive = completedArchive(context)
        if (archive != null) {
            cancelAutomaticDownload(context)
            val installed = RimeConfigInstaller.install(
                context,
                archive,
                BuildConfig.RIME_CONFIG_BASELINE_VERSION,
                BuildConfig.RIME_CONFIG_BASELINE_SHA256
            )
            if (installed.isSuccess) return true
        }

        if (attempt < PollDelaysSeconds.lastIndex) {
            schedulePoll(context, attempt + 1)
            return false
        }

        // The system downloader is the persistent primary path. A direct transfer is a
        // bounded fallback for vendor DownloadManager implementations that never finish
        // redirected release assets even though ordinary app networking still works.
        val fallback = runCatching {
            val downloaded = RimeConfigProvisioningTransport.obtain(
                cacheDir = context.cacheDir,
                version = BuildConfig.RIME_CONFIG_BASELINE_VERSION,
                downloadUrl = BuildConfig.RIME_CONFIG_BASELINE_URL,
                expectedSha256 = BuildConfig.RIME_CONFIG_BASELINE_SHA256
            )
            RimeConfigInstaller.install(
                context,
                downloaded,
                BuildConfig.RIME_CONFIG_BASELINE_VERSION,
                BuildConfig.RIME_CONFIG_BASELINE_SHA256
            ).getOrThrow()
        }
        if (fallback.isSuccess) {
            cancelAutomaticDownload(context)
        } else {
            Timber.w(
                fallback.exceptionOrNull(),
                "Rime configuration provisioning paused"
            )
        }
        return true
    }

    private fun cancelAutomaticDownload(context: Context) {
        runCatching {
            UpdateDownloader.cancelAutomatic(
                context,
                UpdateComponent.RIME_CONFIG,
                BuildConfig.RIME_CONFIG_BASELINE_VERSION
            )
        }.onFailure {
            Timber.w(it, "Unable to cancel automatic Rime configuration download")
        }
    }

    private fun completedArchive(context: Context): java.io.File? {
        val checkpoint = RimeConfigProvisioningTransport.checkpointFile(
            context.cacheDir,
            BuildConfig.RIME_CONFIG_BASELINE_VERSION
        )
        if (
            RimeConfigProvisioningTransport.isVerified(
                checkpoint,
                BuildConfig.RIME_CONFIG_BASELINE_SHA256
            )
        ) {
            return checkpoint
        }
        val downloaded = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.resolve(ArchiveName)
            ?: return null
        return runCatching {
            RimeConfigProvisioningTransport.adopt(
                source = downloaded,
                cacheDir = context.cacheDir,
                version = BuildConfig.RIME_CONFIG_BASELINE_VERSION,
                expectedSize = BuildConfig.RIME_CONFIG_BASELINE_SIZE,
                expectedSha256 = BuildConfig.RIME_CONFIG_BASELINE_SHA256
            )
        }.onFailure {
            Timber.w(it, "Unable to checkpoint DownloadManager Rime archive")
        }.getOrNull()
    }

    private fun requiredArtifact() = UpdateArtifact(
        component = UpdateComponent.RIME_CONFIG,
        version = BuildConfig.RIME_CONFIG_BASELINE_VERSION,
        pageUrl = ReleasePage,
        assets = listOf(
            UpdateArtifact.Asset(
                name = ArchiveName,
                downloadUrl = BuildConfig.RIME_CONFIG_BASELINE_URL
            )
        )
    )

    private fun isReady(context: Context): Boolean {
        val installedVersion = RimeConfigVersionStore.currentVersion(context) ?: return false
        return RimeConfigVersionStore.isHealthy(context) &&
            UpdateVersion.compare(
                installedVersion,
                BuildConfig.RIME_CONFIG_BASELINE_VERSION
            ) >= 0
    }

}

object RimePluginPackagePolicy {
    private const val ReleasePackage = "org.fcitx.fcitx5.android.plugin.rime"
    private const val DebugPackage = "$ReleasePackage.debug"

    fun isCurrentVariant(packageName: String?, debug: Boolean = BuildConfig.DEBUG): Boolean =
        packageName == if (debug) DebugPackage else ReleasePackage
}

class RimePluginPackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED &&
            intent.action != Intent.ACTION_PACKAGE_REPLACED
        ) {
            return
        }
        if (!RimePluginPackagePolicy.isCurrentVariant(intent.data?.schemeSpecificPart)) return

        RimeConfigProvisioner.ensureRequiredConfig(context)
        // Package installation already interrupts the plugin boundary. Reload an active daemon
        // now so users never need a second system-IME toggle to discover the new native plugin.
        if (FcitxDaemon.getFirstConnectionOrNull() != null) {
            FcitxDaemon.restartFcitx()
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import android.content.Context
import android.content.pm.PackageManager
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(
    private val installedVersions: InstalledUpdateVersions,
    private val supportedComponents: Set<UpdateComponent>,
    private val fetchAppReleases: suspend () -> String = { fetchReleases(AppReleasesUrl) },
    private val fetchRimeConfigReleases: suspend () -> String = {
        fetchReleases(RimeConfigReleasesUrl)
    }
) {
    sealed class Result {
        data class Available(val releases: List<UpdateRelease>) : Result() {
            val latestArtifacts: List<UpdateArtifact> = UpdatePlan.latestArtifacts(releases)
        }
        data object UpToDate : Result()
        data class Failed(val cause: Throwable) : Result()
    }

    suspend fun check(): Result = coroutineScope {
        val appResult = async {
            runCatching { UpdateReleaseParser.parseAppReleases(fetchAppReleases()) }
        }
        val configResult = async {
            runCatching {
                UpdateReleaseParser.parseRimeConfigReleases(fetchRimeConfigReleases())
            }
        }
        val results = listOf(appResult.await(), configResult.await())
        val releases = results.flatMap { it.getOrDefault(emptyList()) }
        val available = UpdatePlan.availableReleases(
            releases = releases,
            installed = installedVersions,
            supportedComponents = supportedComponents
        )
        when {
            available.isNotEmpty() -> Result.Available(available)
            results.all { it.isSuccess } -> Result.UpToDate
            else -> Result.Failed(results.firstNotNullOf { it.exceptionOrNull() })
        }
    }

    companion object {
        private const val AppReleasesUrl =
            "https://api.github.com/repos/Rizumu85/fcitx5-android-t9-phone/releases?per_page=100"
        private const val RimeConfigReleasesUrl =
            "https://api.github.com/repos/Rizumu85/rime-ice-t9-phone/releases?per_page=100"

        private suspend fun fetchReleases(url: String): String = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "Fcitx5-Android-T9-Updater")
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }
}

object InstalledUpdateVersionsResolver {
    private const val ReleaseRimePackage = "org.fcitx.fcitx5.android.plugin.rime"

    fun resolve(context: Context): InstalledUpdateVersions = InstalledUpdateVersions(
        app = BuildConfig.VERSION_NAME,
        rimePlugin = context.packageManager.versionNameOrNull(ReleaseRimePackage),
        rimeConfig = listOfNotNull(
            RimeConfigVersionStore.currentVersion(context),
            UpdateDownloader.pendingVersion(context, UpdateComponent.RIME_CONFIG)
        ).maxWithOrNull(Comparator(UpdateVersion::compare))
    )

    fun supportedComponents(): Set<UpdateComponent> = if (BuildConfig.DEBUG) {
        // A release APK has a different application ID and signature from a debug build. Offering
        // it as an in-place update would install a second app instead of updating this session.
        setOf(UpdateComponent.RIME_CONFIG)
    } else {
        UpdateComponent.entries.toSet()
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.versionNameOrNull(packageName: String): String? =
        runCatching { getPackageInfo(packageName, 0).versionName }.getOrNull()
}

internal enum class RimeConfigDeploymentRequirement {
    NONE,
    WAIT_FOR_SOURCE,
    REQUIRED
}

internal object RimeConfigDeploymentHealth {
    const val InstallInProgressMarker = ".rime-config-installing"
    const val DeploymentRequiredMarker = ".rime-config-deployment-required"

    private val RequiredSchemas = listOf(
        "t9.schema.yaml",
        "t9_stroke.schema.yaml",
        "t9_zhuyin.schema.yaml"
    )
    private val RequiredCompiledSchemas = listOf(
        "build/t9.prism.bin",
        "build/t9_stroke.prism.bin",
        "build/t9_zhuyin.prism.bin"
    )

    fun sourceHealthy(rimeDir: File): Boolean =
        !rimeDir.resolve(InstallInProgressMarker).exists() &&
            RequiredSchemas.all { rimeDir.resolve(it).isFile }

    fun deploymentRequirement(rimeDir: File): RimeConfigDeploymentRequirement = when {
        !sourceHealthy(rimeDir) -> RimeConfigDeploymentRequirement.WAIT_FOR_SOURCE
        rimeDir.resolve(DeploymentRequiredMarker).exists() ||
            RequiredCompiledSchemas.any { !rimeDir.resolve(it).isFile } ->
            RimeConfigDeploymentRequirement.REQUIRED
        else -> RimeConfigDeploymentRequirement.NONE
    }
}

object RimeConfigVersionStore {
    private const val PreferenceName = "rime_config_update"
    private const val VersionKey = "installed_version"
    private const val ArchiveSha256Key = "installed_archive_sha256"
    private const val VersionMarker = ".rime-ice-t9-version"
    private const val LegacyUntrackedVersion = "3.0.0"

    fun currentVersion(context: Context): String? {
        val preferences = context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
        preferences.getString(VersionKey, null)?.let { return it }
        val rimeDir = rimeDir(context)
        rimeDir.resolve(VersionMarker).takeIf(File::isFile)?.readText()?.trim()
            ?.takeIf(String::isNotEmpty)?.let { return it }
        // Untracked copies predate provisioning and are known to be v3.0.0. Using the current app
        // baseline here would falsely bless old files whenever a future app raises its requirement.
        return LegacyUntrackedVersion.takeIf {
            rimeDir.resolve("t9.schema.yaml").isFile
        }
    }

    fun isHealthy(context: Context): Boolean {
        return RimeConfigDeploymentHealth.sourceHealthy(rimeDir(context))
    }

    internal fun deploymentRequirement(context: Context): RimeConfigDeploymentRequirement =
        RimeConfigDeploymentHealth.deploymentRequirement(rimeDir(context))

    fun currentArchiveSha256(context: Context): String? =
        context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
            .getString(ArchiveSha256Key, null)

    fun beginInstall(context: Context, version: String) {
        writeMarker(
            rimeDir(context).resolve(RimeConfigDeploymentHealth.InstallInProgressMarker),
            version
        )
    }

    fun record(context: Context, version: String, archiveSha256: String? = null) {
        val rimeDir = rimeDir(context).apply(File::mkdirs)
        writeMarker(rimeDir.resolve(VersionMarker), version)
        val receiptCommitted = context
            .getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
            .edit()
            .putString(VersionKey, version)
            .apply {
                if (archiveSha256 == null) remove(ArchiveSha256Key)
                else putString(ArchiveSha256Key, archiveSha256)
            }
            .commit()
        check(receiptCommitted) { "Unable to record the Rime configuration receipt" }
        // Source installation and native compilation are separate transactions. Keeping this
        // durable handoff before clearing the install marker prevents a process death from ever
        // presenting an uncompiled schema tree as ready.
        writeMarker(
            rimeDir.resolve(RimeConfigDeploymentHealth.DeploymentRequiredMarker),
            version
        )
        check(rimeDir.resolve(RimeConfigDeploymentHealth.InstallInProgressMarker).delete()) {
            "Unable to complete the Rime configuration transaction"
        }
    }

    internal fun completeDeployment(context: Context): Boolean {
        val marker = rimeDir(context)
            .resolve(RimeConfigDeploymentHealth.DeploymentRequiredMarker)
        return !marker.exists() || marker.delete()
    }

    private fun writeMarker(file: File, value: String) {
        file.parentFile?.mkdirs()
        val marker = AtomicFile(file)
        val output = marker.startWrite()
        try {
            output.write(value.toByteArray())
            marker.finishWrite(output)
        } catch (error: Throwable) {
            marker.failWrite(output)
            throw error
        }
    }

    fun rimeDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "data/rime")
}

class AutomaticUpdateCheckGate(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val preferences =
        context.getSharedPreferences("update_checks", Context.MODE_PRIVATE)

    fun tryAcquire(): Boolean {
        val current = now()
        val previous = preferences.getLong(LastAttemptKey, Long.MIN_VALUE)
        if (!AutomaticUpdateCheckPolicy.canAttempt(previous, current)) {
            return false
        }
        // Record attempts, not only successes, so unavailable GitHub never causes repeated errors.
        preferences.edit().putLong(LastAttemptKey, current).apply()
        return true
    }

    private companion object {
        const val LastAttemptKey = "last_automatic_attempt"
    }
}

object AutomaticUpdateCheckPolicy {
    private const val OneDayMs = 24L * 60L * 60L * 1_000L

    fun canAttempt(previous: Long, current: Long): Boolean =
        previous == Long.MIN_VALUE || current < previous || current - previous >= OneDayMs
}

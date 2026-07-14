/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import android.content.Context
import android.content.pm.PackageManager
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
    private val fetchAppRelease: suspend () -> String = { fetchLatestRelease(AppReleaseUrl) },
    private val fetchRimeConfigRelease: suspend () -> String = {
        fetchLatestRelease(RimeConfigReleaseUrl)
    }
) {
    sealed class Result {
        data class Available(val artifacts: List<UpdateArtifact>) : Result()
        data object UpToDate : Result()
        data class Failed(val cause: Throwable) : Result()
    }

    suspend fun check(): Result = coroutineScope {
        val appResult = async {
            runCatching { UpdateReleaseParser.parseAppRelease(fetchAppRelease()) }
        }
        val configResult = async {
            runCatching { UpdateReleaseParser.parseRimeConfigRelease(fetchRimeConfigRelease()) }
        }
        val results = listOf(appResult.await(), configResult.await())
        val artifacts = results.flatMap { it.getOrDefault(emptyList()) }
        val available = UpdatePlan.availableArtifacts(
            artifacts = artifacts,
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
        private const val AppReleaseUrl =
            "https://api.github.com/repos/Rizumu85/fcitx5-android-t9-phone/releases/latest"
        private const val RimeConfigReleaseUrl =
            "https://api.github.com/repos/Rizumu85/rime-ice-t9-phone/releases/latest"

        private suspend fun fetchLatestRelease(url: String): String = withContext(Dispatchers.IO) {
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
        rimeConfig = RimeConfigVersionStore.currentVersion(context)
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

object RimeConfigVersionStore {
    private const val PreferenceName = "rime_config_update"
    private const val VersionKey = "installed_version"
    private const val VersionMarker = ".rime-ice-t9-version"

    fun currentVersion(context: Context): String? {
        val preferences = context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
        preferences.getString(VersionKey, null)?.let { return it }
        val rimeDir = rimeDir(context)
        rimeDir.resolve(VersionMarker).takeIf(File::isFile)?.readText()?.trim()
            ?.takeIf(String::isNotEmpty)?.let { return it }
        // v4.3.0 was distributed with rime-ice-t9-phone v3.0.0 before in-app tracking existed.
        // Recognizing that documented baseline avoids advertising the same package as an update.
        return BuildConfig.RIME_CONFIG_BASELINE_VERSION.takeIf {
            rimeDir.resolve("t9.schema.yaml").isFile
        }
    }

    fun record(context: Context, version: String) {
        val rimeDir = rimeDir(context).apply(File::mkdirs)
        rimeDir.resolve(VersionMarker).writeText(version)
        context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
            .edit()
            .putString(VersionKey, version)
            .apply()
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

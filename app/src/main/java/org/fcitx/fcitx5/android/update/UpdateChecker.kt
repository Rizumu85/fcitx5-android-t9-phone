/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(
    private val fetchRelease: suspend () -> String = ::fetchLatestRelease
) {
    sealed class Result {
        data class Available(val release: UpdateRelease) : Result()
        data object UpToDate : Result()
        data class Failed(val cause: Throwable) : Result()
    }

    suspend fun check(): Result = runCatching {
        UpdateReleaseParser.parse(fetchRelease())
    }.fold(
        onSuccess = { release ->
            if (UpdateVersion.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                Result.Available(release)
            } else {
                Result.UpToDate
            }
        },
        onFailure = Result::Failed
    )

    companion object {
        private const val LatestReleaseUrl =
            "https://api.github.com/repos/Rizumu85/fcitx5-android-t9-phone/releases/latest"

        private suspend fun fetchLatestRelease(): String = withContext(Dispatchers.IO) {
            val connection = URL(LatestReleaseUrl).openConnection() as HttpURLConnection
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

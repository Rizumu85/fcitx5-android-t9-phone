/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import android.app.ActivityManager
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.utils.notificationManager

object UpdateAssetSelector {
    fun asset(
        artifact: UpdateArtifact,
        supportedAbis: List<String>
    ): UpdateArtifact.Asset? = when (artifact.component) {
        UpdateComponent.RIME_CONFIG -> artifact.assets.firstOrNull()
        UpdateComponent.APP,
        UpdateComponent.RIME_PLUGIN -> supportedAbis.firstNotNullOfOrNull { abi ->
            artifact.assets.firstOrNull { it.name.endsWith("-$abi-release.apk") }
        }
    }
}

object UpdateDownloader {
    @Serializable
    data class Pending(
        val component: UpdateComponent,
        val version: String,
        val automatic: Boolean,
        val expectedSha256: String? = null
    )

    data class Options(
        val automatic: Boolean = false,
        val expectedSha256: String? = null
    )

    private const val PreferenceName = "update_downloads"
    private const val PendingPrefix = "pending_"
    private const val ApkMimeType = "application/vnd.android.package-archive"
    private const val ZipMimeType = "application/zip"
    private val json = Json { ignoreUnknownKeys = true }

    /** Probes download sources over the network, so it must not run on the main thread. */
    fun enqueue(
        context: Context,
        artifact: UpdateArtifact,
        options: Options = Options()
    ): Boolean {
        if (hasPending(context, artifact.component, artifact.version)) return true
        val asset = UpdateAssetSelector.asset(artifact, Build.SUPPORTED_ABIS.toList())
            ?: return false
        val expectedSha256 = options.expectedSha256 ?: asset.sha256
        // Without a trusted digest only GitHub's TLS authenticates the file, so it never falls
        // back to a mirror. The app APK is the exception: it always replaces this signed package,
        // and the installer rejects any other signature. The plugin may be a first install.
        val downloadUrl = UpdateDownloadSources.select(
            url = asset.downloadUrl,
            allowMirrors = expectedSha256 != null || artifact.component == UpdateComponent.APP
        )
        val manager = context.getSystemService(DownloadManager::class.java)
        val mimeType = if (artifact.component == UpdateComponent.RIME_CONFIG) {
            ZipMimeType
        } else {
            ApkMimeType
        }
        // DownloadManager rejects an existing destination. The release asset itself is immutable,
        // so replacing a stale prior attempt is preferable to silently opening the web page.
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.resolve(asset.name)
            ?.delete()
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle(asset.name)
            .setMimeType(mimeType)
            .setNotificationVisibility(
                if (options.automatic) DownloadManager.Request.VISIBILITY_VISIBLE
                else DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, asset.name)
        val id = manager.enqueue(request)
        context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
            .edit()
            .putString(
                "$PendingPrefix$id",
                json.encodeToString(
                    Pending(
                        component = artifact.component,
                        version = artifact.version,
                        automatic = options.automatic,
                        expectedSha256 = expectedSha256
                    )
                )
            )
            .apply()
        return true
    }

    internal fun consumePending(context: Context, id: Long): Pending? {
        val preferences = context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
        val key = "$PendingPrefix$id"
        val encoded = preferences.getString(key, null) ?: return null
        preferences.edit().remove(key).apply()
        return runCatching { json.decodeFromString<Pending>(encoded) }.getOrNull()
    }

    fun hasPending(context: Context, component: UpdateComponent, version: String): Boolean {
        val manager = context.getSystemService(DownloadManager::class.java)
        return pendingEntries(context).any { (id, pending) ->
            if (pending.component != component || pending.version != version) {
                return@any false
            }
            when (manager.status(id)) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_SUCCESSFUL -> true
                else -> {
                    consumePending(context, id)
                    false
                }
            }
        }
    }

    fun pendingVersion(context: Context, component: UpdateComponent): String? {
        val manager = context.getSystemService(DownloadManager::class.java)
        return pendingEntries(context)
            .filter { (id, pending) ->
                pending.component == component &&
                    manager.status(id) in setOf(
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PAUSED,
                        DownloadManager.STATUS_SUCCESSFUL
                    )
            }
            .map { it.second.version }
            .maxWithOrNull(UpdateVersion::compare)
    }

    fun cancelAutomatic(context: Context, component: UpdateComponent, version: String) {
        val manager = context.getSystemService(DownloadManager::class.java)
        pendingEntries(context).forEach { (id, pending) ->
            if (pending.automatic && pending.component == component && pending.version == version) {
                manager.remove(id)
                consumePending(context, id)
            }
        }
    }

    private fun pendingEntries(context: Context): List<Pair<Long, Pending>> {
        val preferences = context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
        return preferences.all.mapNotNull { (key, value) ->
            val id = key.removePrefix(PendingPrefix).toLongOrNull()
                ?.takeIf { key.startsWith(PendingPrefix) }
                ?: return@mapNotNull null
            val pending = (value as? String)?.let {
                runCatching { json.decodeFromString<Pending>(it) }.getOrNull()
            }
            if (pending == null) {
                preferences.edit().remove(key).apply()
                null
            } else {
                id to pending
            }
        }
    }

    private fun DownloadManager.status(id: Long): Int? =
        query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }

    private fun <T> Iterable<T>.maxWithOrNull(compare: (T, T) -> Int): T? {
        val iterator = iterator()
        if (!iterator.hasNext()) return null
        var best = iterator.next()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (compare(candidate, best) > 0) best = candidate
        }
        return best
    }
}

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val pending = id.takeIf { it >= 0 }?.let { UpdateDownloader.consumePending(context, it) }
            ?: return
        val manager = context.getSystemService(DownloadManager::class.java)
        if (pending.component == UpdateComponent.RIME_CONFIG) {
            installRimeConfig(context.applicationContext, manager, id, pending)
        } else {
            openApkInstaller(context.applicationContext, manager, id, pending)
        }
    }

    private fun openApkInstaller(
        context: Context,
        manager: DownloadManager,
        downloadId: Long,
        pending: UpdateDownloader.Pending
    ) {
        val result = goAsync()
        Thread({
            val uri = manager.getUriForDownloadedFile(downloadId)
            // A mirror may serve a truncated or replaced file; never hand it to the installer.
            val verified = uri != null && pending.expectedSha256?.let { expected ->
                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(
                        manager.openDownloadedFile(downloadId)
                    ).use { it.sha256() }.equals(expected, ignoreCase = true)
                }.getOrDefault(false)
            } != false
            android.os.Handler(context.mainLooper).post {
                if (verified) {
                    val installIntent = Intent(Intent.ACTION_VIEW, uri)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    if (
                        activityStartLikelyAllowed() ||
                        !postInstallNotification(context, installIntent, pending.component)
                    ) {
                        context.startActivity(installIntent)
                    }
                } else {
                    manager.remove(downloadId)
                    Toast.makeText(
                        context,
                        R.string.update_download_verification_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
                result.finish()
            }
        }, "UpdateApkVerify").start()
    }

    /**
     * Android 10+ silently drops activity starts from a receiver while the app has no visible
     * window, which is the usual case once a long download finishes.
     */
    private fun activityStartLikelyAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    /** Returns false when notifications are unavailable, so the caller can still try a start. */
    private fun postInstallNotification(
        context: Context,
        installIntent: Intent,
        component: UpdateComponent
    ): Boolean {
        val manager = context.notificationManager
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    InstallChannelId,
                    context.getText(R.string.update_available),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = InstallChannelId }
            )
        }
        val notification = NotificationCompat.Builder(context, InstallChannelId)
            .setSmallIcon(R.drawable.ic_baseline_download_24)
            .setContentTitle(context.getText(R.string.app_name))
            .setContentText(context.getText(R.string.update_ready_to_install))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    component.ordinal,
                    installIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setAutoCancel(true)
            .build()
        return runCatching {
            manager.notify(InstallNotificationId + component.ordinal, notification)
        }.isSuccess
    }

    private fun java.io.InputStream.sha256(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun installRimeConfig(
        context: Context,
        manager: DownloadManager,
        downloadId: Long,
        pending: UpdateDownloader.Pending
    ) {
        val result = goAsync()
        Thread({
            // Some vendor DownloadProviders return an all_downloads URI that the requesting app
            // cannot reopen. DownloadManager retains the caller-authorized file handle instead.
            val installed = RimeConfigInstaller.install(context, pending) {
                ParcelFileDescriptor.AutoCloseInputStream(
                    manager.openDownloadedFile(downloadId)
                )
            }.isSuccess
            if (!pending.automatic) android.os.Handler(context.mainLooper).post {
                Toast.makeText(
                    context,
                    if (installed) R.string.rime_config_update_installed
                    else R.string.rime_config_update_failed,
                    Toast.LENGTH_LONG
                ).show()
            }
            result.finish()
        }, "RimeConfigUpdate").start()
    }

    private companion object {
        const val InstallChannelId = "update-install"
        const val InstallNotificationId = 0x7570
    }
}

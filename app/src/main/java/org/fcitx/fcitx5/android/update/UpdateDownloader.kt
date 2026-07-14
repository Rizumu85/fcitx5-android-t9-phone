/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import java.io.File

object UpdateAssetSelector {
    fun asset(
        artifact: UpdateArtifact,
        supportedAbis: List<String>
    ): UpdateArtifact.Asset? = when (artifact.component) {
        UpdateComponent.RIME_CONFIG -> artifact.assets.singleOrNull()
        UpdateComponent.APP,
        UpdateComponent.RIME_PLUGIN -> supportedAbis.firstNotNullOfOrNull { abi ->
            artifact.assets.firstOrNull { it.name.endsWith("-$abi-release.apk") }
        }
    }
}

object UpdateDownloader {
    data class Pending(
        val component: UpdateComponent,
        val version: String
    )

    private const val PreferenceName = "update_downloads"
    private const val PendingPrefix = "pending_"
    private const val ApkMimeType = "application/vnd.android.package-archive"
    private const val ZipMimeType = "application/zip"

    fun enqueue(context: Context, artifact: UpdateArtifact): Boolean {
        val asset = UpdateAssetSelector.asset(artifact, Build.SUPPORTED_ABIS.toList())
            ?: return false
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
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle(asset.name)
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, asset.name)
        val id = manager.enqueue(request)
        context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
            .edit()
            .putString("$PendingPrefix$id", "${artifact.component.name}|${artifact.version}")
            .apply()
        return true
    }

    internal fun consumePending(context: Context, id: Long): Pending? {
        val preferences = context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
        val key = "$PendingPrefix$id"
        val encoded = preferences.getString(key, null) ?: return null
        preferences.edit().remove(key).apply()
        val parts = encoded.split('|', limit = 2)
        return Pending(
            component = parts.getOrNull(0)?.let { runCatching { UpdateComponent.valueOf(it) }.getOrNull() }
                ?: return null,
            version = parts.getOrNull(1).orEmpty()
        )
    }
}

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val pending = id.takeIf { it >= 0 }?.let { UpdateDownloader.consumePending(context, it) }
            ?: return
        val uri = context.getSystemService(DownloadManager::class.java)
            .getUriForDownloadedFile(id) ?: return
        if (pending.component == UpdateComponent.RIME_CONFIG) {
            installRimeConfig(context.applicationContext, uri, pending.version)
        } else {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    private fun installRimeConfig(context: Context, uri: Uri, version: String) {
        val result = goAsync()
        Thread({
            val installed = runCatching {
                val staging = File(context.cacheDir, "rime-config-update")
                val fcitxWasRunning = FcitxDaemon.getFirstConnectionOrNull() != null
                // Rime reads several related YAML files during deployment. Stop it around the
                // overlay so it can never observe a half-old, half-new configuration tree.
                if (fcitxWasRunning) FcitxDaemon.stopFcitx()
                try {
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Downloaded Rime configuration is unavailable" }
                        RimeConfigArchive.install(
                            source = input,
                            stagingDir = staging,
                            destinationDir = RimeConfigVersionStore.rimeDir(context)
                        )
                    }
                    RimeConfigVersionStore.record(context, version)
                } finally {
                    if (fcitxWasRunning) FcitxDaemon.startFcitx()
                }
            }.isSuccess
            android.os.Handler(context.mainLooper).post {
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
}

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

object UpdateAssetSelector {
    fun appAsset(release: UpdateRelease, supportedAbis: List<String>): UpdateRelease.Asset? =
        supportedAbis.firstNotNullOfOrNull { abi ->
            release.appAssets.firstOrNull { it.name.endsWith("-$abi-release.apk") }
        }
}

object UpdateDownloader {
    private const val PreferenceName = "update_downloads"
    private const val PendingIdsKey = "pending_ids"
    private const val ApkMimeType = "application/vnd.android.package-archive"

    fun enqueueApp(context: Context, release: UpdateRelease): Boolean {
        val asset = UpdateAssetSelector.appAsset(release, Build.SUPPORTED_ABIS.toList())
            ?: return false
        val manager = context.getSystemService(DownloadManager::class.java)
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle(asset.name)
            .setMimeType(ApkMimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, asset.name)
        val id = manager.enqueue(request)
        val preferences = context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
        preferences.edit()
            .putStringSet(PendingIdsKey, preferences.pendingIds() + id.toString())
            .apply()
        return true
    }

    internal fun consumePending(context: Context, id: Long): Boolean {
        val preferences = context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
        val ids = preferences.pendingIds()
        if (id.toString() !in ids) return false
        preferences.edit().putStringSet(PendingIdsKey, ids - id.toString()).apply()
        return true
    }

    private fun android.content.SharedPreferences.pendingIds(): Set<String> =
        getStringSet(PendingIdsKey, emptySet()).orEmpty().toSet()
}

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id < 0 || !UpdateDownloader.consumePending(context, id)) return
        val uri = context.getSystemService(DownloadManager::class.java)
            .getUriForDownloadedFile(id) ?: return
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}

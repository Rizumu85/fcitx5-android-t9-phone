/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.fcitx.fcitx5.android.R

object UpdateCheckUi {
    fun showAvailable(context: Context, result: UpdateChecker.Result.Available) {
        val artifacts = result.artifacts.sortedBy { it.component.ordinal }
        val labels = artifacts.map { artifact ->
            when (artifact.component) {
                UpdateComponent.APP -> context.getString(
                    R.string.download_app_update,
                    artifact.version
                )
                UpdateComponent.RIME_PLUGIN -> context.getString(
                    R.string.download_rime_plugin_update,
                    artifact.version
                )
                UpdateComponent.RIME_CONFIG -> context.getString(
                    R.string.download_rime_config_update,
                    artifact.version
                )
            }
        }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.update_available)
            .setItems(labels) { _, index ->
                val artifact = artifacts[index]
                if (UpdateDownloader.enqueue(context, artifact)) {
                    Toast.makeText(context, R.string.update_download_started, Toast.LENGTH_SHORT).show()
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(artifact.pageUrl)))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showUpToDate(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.no_update_available)
            .setMessage(R.string.no_update_available_summary)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun showFailure(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.update_check_failed)
            .setMessage(R.string.update_check_network_error)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}

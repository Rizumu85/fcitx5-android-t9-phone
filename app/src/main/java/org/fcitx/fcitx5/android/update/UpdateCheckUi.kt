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
        lateinit var dialog: AlertDialog
        val content = UpdateReleaseHistoryUi(
            context = context,
            releases = result.releases,
            latestArtifacts = result.latestArtifacts,
            onDownload = { artifact ->
                dialog.dismiss()
                startDownload(context, artifact)
            }
        )
        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.update_available)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun startDownload(context: Context, artifact: UpdateArtifact) {
        if (UpdateDownloader.enqueue(context, artifact)) {
            Toast.makeText(context, R.string.update_download_started, Toast.LENGTH_SHORT).show()
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(artifact.pageUrl)))
        }
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

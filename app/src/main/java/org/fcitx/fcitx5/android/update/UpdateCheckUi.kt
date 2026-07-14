/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import org.fcitx.fcitx5.android.R

object UpdateCheckUi {
    fun showAvailable(context: Context, result: UpdateChecker.Result.Available) {
        val release = result.release
        val components = result.components.toList().sortedBy(UpdateComponent::ordinal)
        val labels = components.map { component ->
            when (component) {
                UpdateComponent.APP -> context.getString(R.string.update_app_component, release.version)
                UpdateComponent.RIME -> context.getString(R.string.update_rime_component, release.version)
            }
        }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.update_available, release.version))
            .setItems(labels) { _, index ->
                if (!UpdateDownloader.enqueue(context, release, components[index])) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.view_update) { _, _ ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
            }
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

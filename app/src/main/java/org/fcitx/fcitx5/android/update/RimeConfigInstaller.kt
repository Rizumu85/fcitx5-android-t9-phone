/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import android.content.Context
import android.net.Uri
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object RimeConfigInstaller {
    private val installLock = ReentrantLock()

    fun install(
        context: Context,
        uri: Uri,
        pending: UpdateDownloader.Pending
    ): Result<Unit> = install(context, pending) {
        requireNotNull(context.contentResolver.openInputStream(uri)) {
            "Downloaded Rime configuration is unavailable"
        }
    }

    fun install(
        context: Context,
        archive: File,
        version: String,
        expectedSha256: String
    ): Result<Unit> = install(
        context = context,
        pending = UpdateDownloader.Pending(
            component = UpdateComponent.RIME_CONFIG,
            version = version,
            automatic = true,
            expectedSha256 = expectedSha256
        ),
        openArchive = archive::inputStream
    )

    private fun install(
        context: Context,
        pending: UpdateDownloader.Pending,
        openArchive: () -> InputStream
    ): Result<Unit> = runCatching {
        installLock.withLock {
            val archiveSha256 = openArchive().use { it.sha256() }
            pending.expectedSha256?.let { expected ->
                require(archiveSha256.equals(expected, ignoreCase = true)) {
                    "Downloaded Rime configuration failed SHA-256 verification"
                }
            }
            val installedVersion = RimeConfigVersionStore.currentVersion(context)
            if (RimeConfigVersionStore.isHealthy(context) && installedVersion != null) {
                val comparison = UpdateVersion.compare(installedVersion, pending.version)
                if (comparison > 0 || comparison == 0 &&
                    RimeConfigVersionStore.currentArchiveSha256(context)
                        ?.equals(archiveSha256, ignoreCase = true) == true
                ) {
                    return@withLock
                }
            }

            // Rime reads a related YAML graph rather than one file. Excluding native startup from
            // the overlay is what makes the receipt a trustworthy all-or-nothing boundary.
            FcitxDaemon.withFcitxStopped {
                openArchive().use { input ->
                    RimeConfigArchive.install(
                        source = input,
                        stagingDir = context.cacheDir.resolve("rime-config-update"),
                        destinationDir = RimeConfigVersionStore.rimeDir(context),
                        beforeOverlay = {
                            RimeConfigVersionStore.beginInstall(context, pending.version)
                        }
                    )
                }
                RimeConfigVersionStore.record(
                    context = context,
                    version = pending.version,
                    archiveSha256 = archiveSha256
                )
            }
        }
    }.onFailure {
        Timber.e(it, "Failed to install Rime configuration ${pending.version}")
    }

    private fun java.io.InputStream.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
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
}

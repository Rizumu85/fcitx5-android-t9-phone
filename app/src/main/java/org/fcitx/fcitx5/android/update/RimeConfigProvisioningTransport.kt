/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object RimeConfigProvisioningTransport {
    private const val MaxArchiveBytes = 256L * 1024L * 1024L

    fun obtain(
        cacheDir: File,
        version: String,
        downloadUrl: String,
        expectedSha256: String
    ): File {
        val archive = checkpointFile(cacheDir, version)
        if (isVerified(archive, expectedSha256)) {
            return archive
        }

        val directory = requireNotNull(archive.parentFile).apply(File::mkdirs)
        val partial = directory.resolve("rime-ice-t9-phone-$version.zip.part")
        val connection = URL(downloadUrl).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("User-Agent", "Fcitx5-Android-T9-Provisioner")
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MaxArchiveBytes) {
                            "Rime configuration archive is unexpectedly large"
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        require(partial.sha256().equals(expectedSha256, ignoreCase = true)) {
            partial.delete()
            "Downloaded Rime configuration failed SHA-256 verification"
        }
        archive.delete()
        check(partial.renameTo(archive)) {
            "Unable to checkpoint the downloaded Rime configuration"
        }
        return archive
    }

    fun adopt(
        source: File,
        cacheDir: File,
        version: String,
        expectedSize: Long,
        expectedSha256: String
    ): File? {
        val archive = checkpointFile(cacheDir, version)
        if (isVerified(archive, expectedSha256)) return archive
        if (!source.isFile || source.length() != expectedSize ||
            !isVerified(source, expectedSha256)
        ) {
            return null
        }
        val adopting = archive.resolveSibling("${archive.name}.adopting")
        adopting.parentFile?.mkdirs()
        source.copyTo(adopting, overwrite = true)
        archive.delete()
        check(adopting.renameTo(archive)) {
            "Unable to checkpoint the completed Rime configuration"
        }
        return archive
    }

    fun checkpointFile(cacheDir: File, version: String): File =
        cacheDir.resolve("rime-config-provisioning/rime-ice-t9-phone-$version.zip")

    fun isVerified(file: File, expectedSha256: String): Boolean =
        file.isFile && file.sha256().equals(expectedSha256, ignoreCase = true)

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun File.resolveSibling(name: String): File =
        requireNotNull(parentFile).resolve(name)
}

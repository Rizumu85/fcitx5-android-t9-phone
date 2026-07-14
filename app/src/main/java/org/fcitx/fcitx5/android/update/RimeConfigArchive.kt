/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

object RimeConfigArchive {
    private const val MaxEntries = 2_000
    private const val MaxExpandedBytes = 256L * 1024L * 1024L

    fun install(source: InputStream, stagingDir: File, destinationDir: File) {
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        val roots = linkedSetOf<String>()
        var entryCount = 0
        var expandedBytes = 0L
        val canonicalStaging = stagingDir.canonicalFile
        ZipInputStream(source.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entryCount <= MaxEntries) { "Rime configuration archive has too many files" }
                val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                if (normalizedName.isEmpty()) continue
                roots += normalizedName.substringBefore('/')
                val output = File(stagingDir, normalizedName).canonicalFile
                require(output.path.startsWith(canonicalStaging.path + File.separator)) {
                    "Rime configuration archive contains an unsafe path"
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().buffered().use { target ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            expandedBytes += read
                            require(expandedBytes <= MaxExpandedBytes) {
                                "Rime configuration archive is unexpectedly large"
                            }
                            target.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val contentRoot = roots.singleOrNull()
            ?.let { stagingDir.resolve(it) }
            ?.takeIf(File::isDirectory)
            ?: stagingDir
        require(contentRoot.resolve("t9.schema.yaml").isFile) {
            "Archive does not contain the T9 Rime configuration"
        }
        destinationDir.mkdirs()
        contentRoot.listFiles().orEmpty().forEach { sourceFile ->
            copyOverlay(sourceFile, destinationDir.resolve(sourceFile.name))
        }
        stagingDir.deleteRecursively()
    }

    private fun copyOverlay(source: File, destination: File) {
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles().orEmpty().forEach { child ->
                copyOverlay(child, destination.resolve(child.name))
            }
            return
        }
        destination.parentFile?.mkdirs()
        val temporary = destination.resolveSibling(".${destination.name}.update")
        source.copyTo(temporary, overwrite = true)
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }

    private fun File.resolveSibling(name: String): File =
        requireNotNull(parentFile) { "Update destination must have a parent directory" }.resolve(name)
}

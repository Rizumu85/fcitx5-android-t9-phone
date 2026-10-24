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
    /**
     * Extracts and validates the complete archive without touching the live Rime tree, returning
     * the directory whose children [overlay] copies into it.
     */
    fun stage(source: InputStream, stagingDir: File): File {
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
        val missingSources = RimeConfigDeploymentHealth.RequiredSources
            .filterNot { contentRoot.resolve(it).isFile }
        require(missingSources.isEmpty()) {
            "Archive is missing required T9 Rime sources: ${missingSources.joinToString()}"
        }
        return contentRoot
    }

    /**
     * Copies [contentRoot] over [destinationDir] without deleting user files. If any copy fails,
     * every replaced file is restored and [afterRollback] runs once the previous tree is intact.
     */
    fun overlay(contentRoot: File, destinationDir: File, afterRollback: () -> Unit = {}) {
        // Each entry is a managed destination and its backup, or null when the file is new.
        val journal = mutableListOf<Pair<File, File?>>()
        try {
            destinationDir.mkdirs()
            contentRoot.listFiles().orEmpty().forEach { sourceFile ->
                copyOverlay(sourceFile, destinationDir.resolve(sourceFile.name), journal)
            }
        } catch (error: Throwable) {
            // An in-process failure must leave the previous working tree, not a permanently
            // journaled partial one. Only process death relies on the marker for later repair.
            if (rollBack(journal)) {
                runCatching(afterRollback).exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
        }
        journal.forEach { (_, backup) -> backup?.delete() }
    }

    private fun copyOverlay(source: File, destination: File, journal: MutableList<Pair<File, File?>>) {
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles().orEmpty().forEach { child ->
                copyOverlay(child, destination.resolve(child.name), journal)
            }
            return
        }
        destination.parentFile?.mkdirs()
        val temporary = destination.resolveSibling(".${destination.name}.update")
        try {
            source.copyTo(temporary, overwrite = true)
            val backup = if (destination.exists()) {
                destination.resolveSibling(".${destination.name}.backup").also { backup ->
                    backup.delete()
                    move(destination, backup)
                }
            } else {
                null
            }
            journal += destination to backup
            move(temporary, destination)
        } finally {
            temporary.delete()
        }
    }

    private fun rollBack(journal: List<Pair<File, File?>>): Boolean =
        journal.asReversed().fold(true) { restored, (destination, backup) ->
            runCatching {
                if (backup == null) {
                    check(!destination.exists() || destination.delete())
                } else {
                    check(backup.exists())
                    move(backup, destination)
                }
            }.isSuccess && restored
        }

    private fun move(source: File, target: File) {
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    private fun File.resolveSibling(name: String): File =
        requireNotNull(parentFile) { "Update destination must have a parent directory" }.resolve(name)
}

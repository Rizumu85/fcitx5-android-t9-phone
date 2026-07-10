/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors

internal class SmartEnglishPersistence<T>(
    private val file: File?,
    defaultValue: T,
    private val decode: (List<String>) -> T,
    private val encode: (T) -> String,
    private val executor: Executor = DefaultExecutor,
    private val atomicWrite: (File, String) -> Unit = ::writeAtomically
) {
    private val lock = Any()
    private var pendingSnapshot: T? = null
    private var writeScheduled = false

    @Volatile
    private var currentSnapshot: T = file
        ?.takeIf(File::isFile)
        ?.let { runCatching { decode(it.readLines()) }.getOrNull() }
        ?: defaultValue

    fun snapshot(): T = currentSnapshot

    fun replace(snapshot: T) {
        currentSnapshot = snapshot
        if (file == null) return
        synchronized(lock) {
            pendingSnapshot = snapshot
            if (writeScheduled) return
            writeScheduled = true
        }
        executor.execute(::drainWrites)
    }

    private fun drainWrites() {
        while (true) {
            val snapshot = synchronized(lock) {
                pendingSnapshot.also {
                    pendingSnapshot = null
                    if (it == null) writeScheduled = false
                }
            } ?: return
            runCatching { atomicWrite(requireNotNull(file), encode(snapshot)) }
        }
    }

    companion object {
        internal val DefaultExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "smart-english-persistence").apply { isDaemon = true }
        }

        private fun writeAtomically(file: File, content: String) {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, ".${file.name}.tmp")
            FileOutputStream(temporary).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            check(temporary.renameTo(file)) {
                "Unable to atomically replace ${file.absolutePath}"
            }
        }
    }
}

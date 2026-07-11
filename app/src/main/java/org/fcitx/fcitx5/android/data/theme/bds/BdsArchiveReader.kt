/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme.bds

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

internal class BdsArchive private constructor(
    private val entries: Map<String, ByteArray>
) {
    val paths: Set<String> get() = entries.keys

    fun bytes(path: String): ByteArray? = entries[normalize(path)]

    fun text(path: String): String? = bytes(path)?.toString(Charsets.UTF_8)

    fun firstBytes(vararg paths: String): ByteArray? = paths.firstNotNullOfOrNull(::bytes)

    companion object {
        fun read(bytes: ByteArray): BdsArchive {
            require(bytes.size <= MAX_ARCHIVE_BYTES) { "BDS archive is too large" }
            rejectEncryptedEntries(bytes)

            val entries = linkedMapOf<String, ByteArray>()
            var totalBytes = 0L
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val path = normalize(entry.name)
                        require(path.isNotEmpty() && !path.startsWith("../") && "/../" !in path) {
                            "Unsafe BDS entry path"
                        }
                        val value = zip.readBounded(MAX_ENTRY_BYTES)
                        totalBytes += value.size
                        require(totalBytes <= MAX_EXTRACTED_BYTES) { "BDS archive expands too large" }
                        entries.putIfAbsent(path, value)
                        require(entries.size <= MAX_ENTRY_COUNT) { "BDS archive contains too many files" }
                    }
                    zip.closeEntry()
                }
            }
            require(entries.isNotEmpty()) { "BDS archive is empty" }
            return BdsArchive(entries)
        }

        private fun normalize(path: String): String = path
            .replace('\\', '/')
            .trimStart('/')
            .lowercase(Locale.ROOT)

        private fun rejectEncryptedEntries(bytes: ByteArray) {
            val input = ByteArrayInputStream(bytes)
            while (true) {
                val signature = input.readIntLeOrNull() ?: break
                if (signature != LOCAL_FILE_HEADER) break
                input.skipFully(2)
                val flags = input.readShortLe()
                input.skipFully(10)
                val compressedSize = input.readIntLe().toLong() and 0xffffffffL
                input.skipFully(4)
                val nameLength = input.readShortLe()
                val extraLength = input.readShortLe()
                input.skipFully(nameLength.toLong() + extraLength.toLong())
                if ((flags and ENCRYPTED_FLAG) != 0) {
                    throw ZipException(ENCRYPTED_MESSAGE)
                }
                // Entries with a data descriptor cannot be scanned reliably without inflating.
                // They are safe to hand to ZipInputStream after all preceding headers were checked.
                if ((flags and DATA_DESCRIPTOR_FLAG) != 0) return
                input.skipFully(compressedSize)
            }
        }

        private fun ByteArrayInputStream.readIntLeOrNull(): Int? {
            val b0 = read()
            if (b0 < 0) return null
            val b1 = read(); val b2 = read(); val b3 = read()
            if (b1 < 0 || b2 < 0 || b3 < 0) throw EOFException()
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        private fun ByteArrayInputStream.readIntLe(): Int = readIntLeOrNull() ?: throw EOFException()

        private fun ByteArrayInputStream.readShortLe(): Int {
            val b0 = read(); val b1 = read()
            if (b0 < 0 || b1 < 0) throw EOFException()
            return b0 or (b1 shl 8)
        }

        private fun ByteArrayInputStream.skipFully(count: Long) {
            var remaining = count
            while (remaining > 0L) {
                val skipped = skip(remaining)
                if (skipped > 0L) remaining -= skipped
                else if (read() >= 0) remaining--
                else throw EOFException()
            }
        }

        private fun ZipInputStream.readBounded(limit: Int): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                require(total <= limit) { "BDS entry is too large" }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }

        const val ENCRYPTED_MESSAGE = "BDS_ENCRYPTED"
        private const val LOCAL_FILE_HEADER = 0x04034b50
        private const val ENCRYPTED_FLAG = 0x0001
        private const val DATA_DESCRIPTOR_FLAG = 0x0008
        private const val MAX_ARCHIVE_BYTES = 96 * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 24 * 1024 * 1024
        private const val MAX_EXTRACTED_BYTES = 160L * 1024L * 1024L
        private const val MAX_ENTRY_COUNT = 4096
    }
}

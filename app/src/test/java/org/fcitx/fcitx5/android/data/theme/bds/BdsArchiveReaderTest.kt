/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme.bds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

class BdsArchiveReaderTest {

    @Test
    fun readsEntriesFromLocalHeadersWithoutDependingOnCentralDirectorySizes() {
        val bytes = archive("Light/Port/gen.ini" to "[PANEL]\nBACK_STYLE=1")
        val central = bytes.indexOfSignature(0x02014b50)
        require(central >= 0)
        // Some current BDS creators write 0xffffffff into central-directory sizes even though
        // local headers are valid. Runtime import deliberately follows the local entry stream.
        repeat(8) { offset -> bytes[central + 20 + offset] = 0xff.toByte() }

        val archive = BdsArchive.read(bytes)

        assertEquals("[PANEL]\nBACK_STYLE=1", archive.text("light/port/GEN.INI"))
    }

    @Test
    fun rejectsEncryptedEntriesBeforeExtraction() {
        val bytes = archive("Info.txt" to "Name=Secret")
        bytes[6] = (bytes[6].toInt() or 0x01).toByte()

        val error = assertThrows(ZipException::class.java) { BdsArchive.read(bytes) }

        assertEquals(BdsArchive.ENCRYPTED_MESSAGE, error.message)
    }

    private fun archive(vararg files: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                files.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }

    private fun ByteArray.indexOfSignature(signature: Int): Int {
        for (index in 0..size - 4) {
            val value = (this[index].toInt() and 0xff) or
                ((this[index + 1].toInt() and 0xff) shl 8) or
                ((this[index + 2].toInt() and 0xff) shl 16) or
                ((this[index + 3].toInt() and 0xff) shl 24)
            if (value == signature) return index
        }
        return -1
    }
}

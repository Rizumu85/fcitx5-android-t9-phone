/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RimeConfigArchiveTest {
    @Test
    fun `install strips repository root and preserves user files`() {
        val root = Files.createTempDirectory("rime-update-test").toFile()
        val destination = root.resolve("rime").apply { mkdirs() }
        destination.resolve("custom.user.yaml").writeText("keep")

        RimeConfigArchive.install(
            source = archive(
                "rime-ice-t9-phone-main/t9.schema.yaml" to "new schema",
                "rime-ice-t9-phone-main/t9_stroke.schema.yaml" to "stroke schema",
                "rime-ice-t9-phone-main/t9_zhuyin.schema.yaml" to "zhuyin schema",
                "rime-ice-t9-phone-main/cn_dicts/base.dict.yaml" to "dictionary"
            ),
            stagingDir = root.resolve("staging"),
            destinationDir = destination
        )

        assertEquals("new schema", destination.resolve("t9.schema.yaml").readText())
        assertEquals("dictionary", destination.resolve("cn_dicts/base.dict.yaml").readText())
        assertEquals("keep", destination.resolve("custom.user.yaml").readText())
        root.deleteRecursively()
    }

    @Test
    fun `install rejects an incomplete T9 scheme family`() {
        val root = Files.createTempDirectory("rime-update-test").toFile()
        var overlayStarted = false

        assertThrows(IllegalArgumentException::class.java) {
            RimeConfigArchive.install(
                source = archive("rime-ice-t9-phone-main/t9.schema.yaml" to "pinyin only"),
                stagingDir = root.resolve("staging"),
                destinationDir = root.resolve("rime"),
                beforeOverlay = { overlayStarted = true }
            )
        }
        assertEquals(false, overlayStarted)
        root.deleteRecursively()
    }

    @Test
    fun `install rejects paths outside staging directory`() {
        val root = Files.createTempDirectory("rime-update-test").toFile()

        assertThrows(IllegalArgumentException::class.java) {
            RimeConfigArchive.install(
                source = archive("../t9.schema.yaml" to "unsafe"),
                stagingDir = root.resolve("staging"),
                destinationDir = root.resolve("rime")
            )
        }
        root.deleteRecursively()
    }

    private fun archive(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(output.toByteArray())
    }
}

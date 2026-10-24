/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RimeConfigArchiveTest {
    @Test
    fun `stage and overlay strip repository root and preserves user files`() {
        val root = Files.createTempDirectory("rime-update-test").toFile()
        val destination = root.resolve("rime").apply { mkdirs() }
        destination.resolve("custom.user.yaml").writeText("keep")

        val contentRoot = RimeConfigArchive.stage(
            source = archive(
                "rime-ice-t9-phone-main/t9.schema.yaml" to "new schema",
                "rime-ice-t9-phone-main/t9_stroke.schema.yaml" to "stroke schema",
                "rime-ice-t9-phone-main/t9_zhuyin.schema.yaml" to "zhuyin schema",
                "rime-ice-t9-phone-main/predict.db" to "prediction database",
                "rime-ice-t9-phone-main/cn_dicts/base.dict.yaml" to "dictionary"
            ),
            stagingDir = root.resolve("staging")
        )
        assertFalse(destination.resolve("t9.schema.yaml").exists())
        RimeConfigArchive.overlay(contentRoot, destination)

        assertEquals("new schema", destination.resolve("t9.schema.yaml").readText())
        assertEquals("dictionary", destination.resolve("cn_dicts/base.dict.yaml").readText())
        assertEquals("keep", destination.resolve("custom.user.yaml").readText())
        root.deleteRecursively()
    }

    @Test
    fun `failed overlay restores the previous tree`() {
        val root = Files.createTempDirectory("rime-update-test").toFile()
        val destination = root.resolve("rime").apply { mkdirs() }
        destination.resolve("t9.schema.yaml").writeText("old schema")
        // A regular file where the archive expects a directory makes the overlay fail midway.
        destination.resolve("blocked").writeText("user file")
        val contentRoot = RimeConfigArchive.stage(
            source = archive(
                "rime-ice-t9-phone-main/t9.schema.yaml" to "new schema",
                "rime-ice-t9-phone-main/t9_stroke.schema.yaml" to "stroke schema",
                "rime-ice-t9-phone-main/t9_zhuyin.schema.yaml" to "zhuyin schema",
                "rime-ice-t9-phone-main/predict.db" to "prediction database",
                "rime-ice-t9-phone-main/blocked/child.yaml" to "child"
            ),
            stagingDir = root.resolve("staging")
        )
        var rolledBack = false

        assertThrows(Exception::class.java) {
            RimeConfigArchive.overlay(contentRoot, destination) { rolledBack = true }
        }

        assertTrue(rolledBack)
        assertEquals("old schema", destination.resolve("t9.schema.yaml").readText())
        assertEquals("user file", destination.resolve("blocked").readText())
        assertFalse(destination.resolve("predict.db").exists())
        assertEquals(
            setOf("t9.schema.yaml", "blocked"),
            destination.listFiles().orEmpty().map { it.name }.toSet()
        )
        root.deleteRecursively()
    }

    @Test
    fun `stage rejects an incomplete required source family`() {
        val root = Files.createTempDirectory("rime-update-test").toFile()

        assertThrows(IllegalArgumentException::class.java) {
            RimeConfigArchive.stage(
                source = archive(
                    "rime-ice-t9-phone-main/t9.schema.yaml" to "pinyin schema",
                    "rime-ice-t9-phone-main/t9_stroke.schema.yaml" to "stroke schema",
                    "rime-ice-t9-phone-main/t9_zhuyin.schema.yaml" to "zhuyin schema"
                ),
                stagingDir = root.resolve("staging")
            )
        }
        root.deleteRecursively()
    }

    @Test
    fun `stage rejects paths outside staging directory`() {
        val root = Files.createTempDirectory("rime-update-test").toFile()

        assertThrows(IllegalArgumentException::class.java) {
            RimeConfigArchive.stage(
                source = archive("../t9.schema.yaml" to "unsafe"),
                stagingDir = root.resolve("staging")
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

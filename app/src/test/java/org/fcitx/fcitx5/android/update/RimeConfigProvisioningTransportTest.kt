/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RimeConfigProvisioningTransportTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `completed system download becomes a verified checkpoint`() {
        val bytes = "verified archive".toByteArray()
        val source = temporaryFolder.newFile("download.zip").apply { writeBytes(bytes) }

        val adopted = RimeConfigProvisioningTransport.adopt(
            source = source,
            cacheDir = temporaryFolder.newFolder("cache"),
            version = "3.0.0",
            expectedSize = bytes.size.toLong(),
            expectedSha256 = "040a1170825ade3ff37b189dd280153ecfafb99ee929d1cbebb40fe135afdf26"
        )

        requireNotNull(adopted)
        assertEquals("rime-ice-t9-phone-3.0.0.zip", adopted.name)
        assertArrayEquals(bytes, adopted.readBytes())
    }

    @Test
    fun `partial or untrusted system download is never adopted`() {
        val source = temporaryFolder.newFile("download.zip").apply {
            writeText("partial")
        }
        val cacheDir = temporaryFolder.newFolder("cache")

        assertNull(
            RimeConfigProvisioningTransport.adopt(
                source = source,
                cacheDir = cacheDir,
                version = "3.0.0",
                expectedSize = source.length() + 1,
                expectedSha256 = "unused"
            )
        )
        assertNull(
            RimeConfigProvisioningTransport.adopt(
                source = source,
                cacheDir = cacheDir,
                version = "3.0.0",
                expectedSize = source.length(),
                expectedSha256 = "wrong digest"
            )
        )
    }
}

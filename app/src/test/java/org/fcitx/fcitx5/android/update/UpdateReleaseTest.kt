/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleaseTest {
    @Test
    fun `release parser separates app and rime assets`() {
        val release = UpdateReleaseParser.parse(
            """
            {
              "tag_name": "v4.4.0",
              "html_url": "https://example.com/release",
              "assets": [
                {
                  "name": "org.fcitx.fcitx5.android-4.4.0-arm64-v8a-release.apk",
                  "browser_download_url": "https://example.com/app.apk"
                },
                {
                  "name": "org.fcitx.fcitx5.android.plugin.rime-4.4.0-arm64-v8a-release.apk",
                  "browser_download_url": "https://example.com/rime.apk"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("4.4.0", release.version)
        assertEquals(1, release.appAssets.size)
        assertEquals(1, release.rimeAssets.size)
    }

    @Test
    fun `version comparison handles different segment counts`() {
        assertTrue(UpdateVersion.isNewer("4.4.0", "4.3.9"))
        assertTrue(UpdateVersion.isNewer("4.3.1", "4.3"))
        assertFalse(UpdateVersion.isNewer("4.3.0", "4.3.0"))
        assertFalse(UpdateVersion.isNewer("4.2.9", "4.3.0"))
    }

    @Test
    fun `automatic check gate permits at most one attempt per day`() {
        val hour = 60L * 60L * 1_000L

        assertTrue(AutomaticUpdateCheckPolicy.canAttempt(Long.MIN_VALUE, 10L * hour))
        assertFalse(AutomaticUpdateCheckPolicy.canAttempt(10L * hour, 20L * hour))
        assertTrue(AutomaticUpdateCheckPolicy.canAttempt(10L * hour, 34L * hour))
        assertTrue(AutomaticUpdateCheckPolicy.canAttempt(10L * hour, 9L * hour))
    }

    @Test
    fun `app asset follows device abi preference`() {
        val release = UpdateRelease(
            version = "4.4.0",
            pageUrl = "https://example.com",
            appAssets = listOf(
                UpdateRelease.Asset("app-4.4.0-armeabi-v7a-release.apk", "32"),
                UpdateRelease.Asset("app-4.4.0-arm64-v8a-release.apk", "64")
            ),
            rimeAssets = emptyList()
        )

        assertEquals(
            "64",
            UpdateAssetSelector.appAsset(release, listOf("arm64-v8a", "armeabi-v7a"))?.downloadUrl
        )
    }
}

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
    fun `app release parser creates independent app and plugin artifacts`() {
        val artifacts = UpdateReleaseParser.parseAppRelease(
            releaseJson(
                version = "4.4.0",
                assets = listOf(
                    "org.fcitx.fcitx5.android-4.4.0-arm64-v8a-release.apk" to "app",
                    "org.fcitx.fcitx5.android.plugin.rime-4.4.0-arm64-v8a-release.apk" to "plugin"
                )
            )
        )

        assertEquals(listOf(UpdateComponent.APP, UpdateComponent.RIME_PLUGIN), artifacts.map { it.component })
        assertEquals(listOf("4.4.0", "4.4.0"), artifacts.map { it.version })
    }

    @Test
    fun `rime config parser keeps its own release version`() {
        val artifacts = UpdateReleaseParser.parseRimeConfigRelease(
            releaseJson(
                version = "3.1.0",
                assets = listOf("rime-ice-t9-phone-main.zip" to "config")
            )
        )

        assertEquals(UpdateComponent.RIME_CONFIG, artifacts.single().component)
        assertEquals("3.1.0", artifacts.single().version)
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
    fun `apk asset follows device abi preference while config is architecture independent`() {
        val app = artifact(
            UpdateComponent.APP,
            "4.4.0",
            "app-4.4.0-armeabi-v7a-release.apk" to "32",
            "app-4.4.0-arm64-v8a-release.apk" to "64"
        )
        val config = artifact(
            UpdateComponent.RIME_CONFIG,
            "3.1.0",
            "rime-ice-t9-phone-main.zip" to "config"
        )

        assertEquals("64", UpdateAssetSelector.asset(app, listOf("arm64-v8a", "armeabi-v7a"))?.downloadUrl)
        assertEquals("config", UpdateAssetSelector.asset(config, emptyList())?.downloadUrl)
    }

    @Test
    fun `update plan compares each component version independently`() {
        val artifacts = listOf(
            artifact(UpdateComponent.APP, "4.3.0", "app.apk" to "app"),
            artifact(UpdateComponent.RIME_PLUGIN, "4.3.0", "plugin.apk" to "plugin"),
            artifact(UpdateComponent.RIME_CONFIG, "3.0.0", "config.zip" to "config")
        )

        assertEquals(
            listOf(UpdateComponent.RIME_PLUGIN),
            UpdatePlan.availableArtifacts(
                artifacts,
                InstalledUpdateVersions(app = "4.3.0", rimePlugin = "4.2.0", rimeConfig = "3.0.0")
            ).map { it.component }
        )
        assertEquals(
            emptyList<UpdateArtifact>(),
            UpdatePlan.availableArtifacts(
                artifacts,
                InstalledUpdateVersions(app = "4.3.0", rimePlugin = null, rimeConfig = "3.0.0"),
                supportedComponents = setOf(UpdateComponent.RIME_CONFIG)
            )
        )
    }

    private fun artifact(
        component: UpdateComponent,
        version: String,
        vararg assets: Pair<String, String>
    ) = UpdateArtifact(
        component = component,
        version = version,
        pageUrl = "https://example.com/release",
        assets = assets.map { UpdateArtifact.Asset(it.first, it.second) }
    )

    private fun releaseJson(version: String, assets: List<Pair<String, String>>): String =
        """
        {
          "tag_name": "v$version",
          "html_url": "https://example.com/release",
          "assets": [
            ${assets.joinToString(",") { (name, url) ->
                """{"name":"$name","browser_download_url":"$url"}"""
            }}
          ]
        }
        """.trimIndent()
}

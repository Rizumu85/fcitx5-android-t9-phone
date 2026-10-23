/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleaseTest {
    @Test
    fun `app release parser creates independent app and plugin artifacts`() {
        val releases = UpdateReleaseParser.parseAppReleases(
            releasesJson(
                version = "4.4.0",
                assets = listOf(
                    "org.fcitx.fcitx5.android-4.4.0-arm64-v8a-release.apk" to "app",
                    "org.fcitx.fcitx5.android.plugin.rime-4.4.0-arm64-v8a-release.apk" to "plugin"
                )
            )
        )
        val artifacts = releases.single().artifacts

        assertEquals(listOf(UpdateComponent.APP, UpdateComponent.RIME_PLUGIN), artifacts.map { it.component })
        assertEquals(listOf("4.4.0", "4.4.0"), artifacts.map { it.version })
        assertEquals("Release 4.4.0", releases.single().title)
        assertEquals("Notes for 4.4.0", releases.single().notes)
    }

    @Test
    fun `rime config parser keeps its own release version`() {
        val releases = UpdateReleaseParser.parseRimeConfigReleases(
            releasesJson(
                version = "3.1.0",
                assets = listOf("rime-ice-t9-phone-main.zip" to "config")
            )
        )
        val artifacts = releases.single().artifacts

        assertEquals(UpdateComponent.RIME_CONFIG, artifacts.single().component)
        assertEquals("3.1.0", artifacts.single().version)
    }

    @Test
    fun `asset digests are parsed as lowercase sha256 hex`() {
        val releases = UpdateReleaseParser.parseRimeConfigReleases(
            releasesJson(
                version = "3.1.0",
                assets = listOf("rime-ice-t9-phone-main.zip" to "config"),
                digest = "sha256:ABCDEF0123"
            )
        )

        assertEquals("abcdef0123", releases.single().artifacts.single().assets.single().sha256)
    }

    @Test
    fun `config digests from mirrored metadata are not trusted`() {
        val url = "https://github.com/Rizumu85/rime-ice-t9-phone/releases/download/" +
            "v3.1.0/rime-ice-t9-phone-main.zip"
        val releases = UpdateReleaseParser.parseRimeConfigReleases(
            releasesJson(
                version = "3.1.0",
                assets = listOf("rime-ice-t9-phone-main.zip" to url),
                digest = "sha256:abcdef0123"
            ),
            trustDigests = false
        )

        val asset = releases.single().artifacts.single().assets.single()
        assertEquals(url, asset.downloadUrl)
        assertNull(asset.sha256)
    }

    @Test
    fun `mirrored metadata cannot redirect the config archive to another host`() {
        val releases = UpdateReleaseParser.parseRimeConfigReleases(
            releasesJson(
                version = "3.1.0",
                assets = listOf(
                    "rime-ice-t9-phone-main.zip" to
                        "https://github.com/someone-else/repo/releases/download/v3.1.0/rime-ice-t9-phone-main.zip"
                )
            ),
            trustDigests = false
        )

        assertTrue(releases.isEmpty())
    }

    @Test
    fun `non sha256 digests are ignored`() {
        val releases = UpdateReleaseParser.parseAppReleases(
            releasesJson(
                version = "4.4.0",
                assets = listOf("org.fcitx.fcitx5.android-4.4.0-arm64-v8a-release.apk" to "app"),
                digest = "md5:abcdef"
            )
        )

        assertNull(releases.single().artifacts.single().assets.single().sha256)
    }

    @Test
    fun `mirror candidates follow the direct GitHub url and skip other hosts`() {
        val url = "https://github.com/owner/repo/releases/download/v1/file.zip"

        val candidates = UpdateDownloadSources.candidates(url)

        assertEquals(url, candidates.first())
        assertEquals(UpdateDownloadSources.MirrorPrefixes.map { it + url }, candidates.drop(1))
        assertEquals(
            listOf("https://example.com/file.zip"),
            UpdateDownloadSources.candidates("https://example.com/file.zip")
        )
    }

    @Test
    fun `api fetch prefers GitHub and marks mirrored payloads`() {
        val url = "https://api.github.com/repos/owner/repo/releases"

        val direct = UpdateDownloadSources.fetchApi(url) { "direct:$it" }
        val mirrored = UpdateDownloadSources.fetchApi(url) { requested ->
            if (requested == url) error("blocked") else "mirror:$requested"
        }

        assertEquals(UpdateDownloadSources.ApiPayload("direct:$url", viaMirror = false), direct)
        assertEquals(
            UpdateDownloadSources.ApiPayload(
                "mirror:${UpdateDownloadSources.ApiMirrorPrefixes.first()}$url",
                viaMirror = true
            ),
            mirrored
        )
    }

    @Test
    fun `available release history is newest first and excludes installed versions`() {
        val releases = listOf(
            release("4.3.0", "2026-07-11T00:00:00Z", UpdateComponent.APP),
            release("4.4.0", "2026-07-14T00:00:00Z", UpdateComponent.APP),
            release("3.1.0", "2026-07-13T00:00:00Z", UpdateComponent.RIME_CONFIG),
            release("4.2.0", "2026-07-10T00:00:00Z", UpdateComponent.APP)
        )

        val available = UpdatePlan.availableReleases(
            releases,
            InstalledUpdateVersions(app = "4.2.0", rimePlugin = null, rimeConfig = "3.0.0"),
            supportedComponents = setOf(UpdateComponent.APP, UpdateComponent.RIME_CONFIG)
        )

        assertEquals(listOf("4.4.0", "3.1.0", "4.3.0"), available.map { it.version })
        assertEquals(
            listOf("4.4.0", "3.1.0"),
            UpdatePlan.latestArtifacts(available).map { it.version }
        )
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

    private fun release(
        version: String,
        publishedAt: String,
        component: UpdateComponent
    ) = UpdateRelease(
        channel = if (component == UpdateComponent.RIME_CONFIG) {
            UpdateReleaseChannel.RIME_CONFIG
        } else {
            UpdateReleaseChannel.APP
        },
        version = version,
        title = "Release $version",
        pageUrl = "https://example.com/release/$version",
        notes = "Notes for $version",
        publishedAt = publishedAt,
        artifacts = listOf(artifact(component, version, "$component-$version" to version))
    )

    private fun releasesJson(
        version: String,
        assets: List<Pair<String, String>>,
        digest: String? = null
    ): String =
        """
        [{
            "tag_name": "v$version",
            "name": "Release $version",
            "html_url": "https://example.com/release",
            "body": "Notes for $version",
            "published_at": "2026-07-14T00:00:00Z",
            "assets": [
              ${assets.joinToString(",") { (name, url) ->
                  val digestField = digest?.let { ""","digest":"$it"""" }.orEmpty()
                  """{"name":"$name","browser_download_url":"$url"$digestField}"""
              }}
            ]
        }]
        """.trimIndent()
}

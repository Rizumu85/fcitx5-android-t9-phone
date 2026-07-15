/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class UpdateComponent {
    APP,
    RIME_PLUGIN,
    RIME_CONFIG
}

enum class UpdateReleaseChannel {
    APP,
    RIME_CONFIG
}

data class UpdateArtifact(
    val component: UpdateComponent,
    val version: String,
    val pageUrl: String,
    val assets: List<Asset>
) {
    data class Asset(
        val name: String,
        val downloadUrl: String
    )
}

data class UpdateRelease(
    val channel: UpdateReleaseChannel,
    val version: String,
    val title: String,
    val pageUrl: String,
    val notes: String,
    val publishedAt: String,
    val artifacts: List<UpdateArtifact>
)

data class InstalledUpdateVersions(
    val app: String,
    val rimePlugin: String?,
    val rimeConfig: String?
) {
    fun versionOf(component: UpdateComponent): String? = when (component) {
        UpdateComponent.APP -> app
        UpdateComponent.RIME_PLUGIN -> rimePlugin
        UpdateComponent.RIME_CONFIG -> rimeConfig
    }
}

object UpdatePlan {
    fun availableArtifacts(
        artifacts: List<UpdateArtifact>,
        installed: InstalledUpdateVersions,
        supportedComponents: Set<UpdateComponent> = UpdateComponent.entries.toSet()
    ): List<UpdateArtifact> = artifacts.filter { artifact ->
        artifact.component in supportedComponents &&
            installed.versionOf(artifact.component)?.let { installedVersion ->
                UpdateVersion.isNewer(artifact.version, installedVersion)
            } != false
    }

    fun availableReleases(
        releases: List<UpdateRelease>,
        installed: InstalledUpdateVersions,
        supportedComponents: Set<UpdateComponent> = UpdateComponent.entries.toSet()
    ): List<UpdateRelease> = releases.mapNotNull { release ->
        val artifacts = availableArtifacts(release.artifacts, installed, supportedComponents)
        release.copy(artifacts = artifacts).takeIf { artifacts.isNotEmpty() }
    }.sortedByDescending(UpdateRelease::publishedAt)

    fun latestArtifacts(releases: List<UpdateRelease>): List<UpdateArtifact> =
        releases.asSequence()
            .flatMap { it.artifacts.asSequence() }
            .groupBy(UpdateArtifact::component)
            .mapNotNull { (_, artifacts) ->
                artifacts.maxWithOrNull { left, right ->
                    UpdateVersion.compare(left.version, right.version)
                }
            }
            .sortedBy { it.component.ordinal }
}

object UpdateReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tagName: String,
        val name: String = "",
        @SerialName("html_url") val pageUrl: String,
        val body: String = "",
        @SerialName("published_at") val publishedAt: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GithubAsset> = emptyList()
    )

    @Serializable
    private data class GithubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String
    )

    fun parseAppReleases(payload: String): List<UpdateRelease> =
        json.decodeFromString<List<GithubRelease>>(payload)
            .filterNot { it.draft || it.prerelease }
            .mapNotNull { release ->
                val assets = release.assets.map { UpdateArtifact.Asset(it.name, it.downloadUrl) }
                val version = release.tagName.removePrefix("v")
                val artifacts = buildList {
                    assets.filter { it.name.matches(APP_APK) }.takeIf(List<*>::isNotEmpty)?.let {
                        add(UpdateArtifact(UpdateComponent.APP, version, release.pageUrl, it))
                    }
                    assets.filter { it.name.matches(RIME_PLUGIN_APK) }
                        .takeIf(List<*>::isNotEmpty)?.let {
                            add(
                                UpdateArtifact(
                                    UpdateComponent.RIME_PLUGIN,
                                    version,
                                    release.pageUrl,
                                    it
                                )
                            )
                        }
                }
                release.toUpdateRelease(UpdateReleaseChannel.APP, version, artifacts)
            }

    fun parseRimeConfigReleases(payload: String): List<UpdateRelease> =
        json.decodeFromString<List<GithubRelease>>(payload)
            .filterNot { it.draft || it.prerelease }
            .mapNotNull { release ->
                val assets = release.assets
                    .filter { it.name == RIME_CONFIG_ARCHIVE }
                    .map { UpdateArtifact.Asset(it.name, it.downloadUrl) }
                val version = release.tagName.removePrefix("v")
                val artifacts = assets.takeIf(List<*>::isNotEmpty)?.let {
                    listOf(
                        UpdateArtifact(
                            component = UpdateComponent.RIME_CONFIG,
                            version = version,
                            pageUrl = release.pageUrl,
                            assets = it
                        )
                    )
                }.orEmpty()
                release.toUpdateRelease(UpdateReleaseChannel.RIME_CONFIG, version, artifacts)
            }

    private fun GithubRelease.toUpdateRelease(
        channel: UpdateReleaseChannel,
        version: String,
        artifacts: List<UpdateArtifact>
    ): UpdateRelease? = artifacts.takeIf(List<*>::isNotEmpty)?.let {
        UpdateRelease(
            channel = channel,
            version = version,
            title = name,
            pageUrl = pageUrl,
            notes = body.trim(),
            publishedAt = publishedAt,
            artifacts = it
        )
    }

    private val APP_APK = Regex("org\\.fcitx\\.fcitx5\\.android-[^-]+-.+-release\\.apk")
    private val RIME_PLUGIN_APK =
        Regex("org\\.fcitx\\.fcitx5\\.android\\.plugin\\.rime-[^-]+-.+-release\\.apk")
    private const val RIME_CONFIG_ARCHIVE = "rime-ice-t9-phone-main.zip"
}

object UpdateVersion {
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun compare(left: String, right: String): Int {
        val leftParts = left.numericParts()
        val rightParts = right.numericParts()
        val size = maxOf(leftParts.size, rightParts.size)
        return (0 until size).firstNotNullOfOrNull { index ->
            leftParts.getOrElse(index) { 0 }
                .compareTo(rightParts.getOrElse(index) { 0 })
                .takeIf { it != 0 }
        } ?: 0
    }

    private fun String.numericParts(): List<Int> =
        removePrefix("v")
            .substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
}

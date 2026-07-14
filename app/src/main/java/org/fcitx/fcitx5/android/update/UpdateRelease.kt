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
}

object UpdateReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val pageUrl: String,
        val assets: List<GithubAsset> = emptyList()
    )

    @Serializable
    private data class GithubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String
    )

    fun parseAppRelease(payload: String): List<UpdateArtifact> {
        val release = json.decodeFromString<GithubRelease>(payload)
        val assets = release.assets.map { UpdateArtifact.Asset(it.name, it.downloadUrl) }
        val version = release.tagName.removePrefix("v")
        return buildList {
            assets.filter { it.name.matches(APP_APK) }.takeIf(List<*>::isNotEmpty)?.let {
                add(UpdateArtifact(UpdateComponent.APP, version, release.pageUrl, it))
            }
            assets.filter { it.name.matches(RIME_PLUGIN_APK) }.takeIf(List<*>::isNotEmpty)?.let {
                add(UpdateArtifact(UpdateComponent.RIME_PLUGIN, version, release.pageUrl, it))
            }
        }
    }

    fun parseRimeConfigRelease(payload: String): List<UpdateArtifact> {
        val release = json.decodeFromString<GithubRelease>(payload)
        val assets = release.assets
            .filter { it.name == RIME_CONFIG_ARCHIVE }
            .map { UpdateArtifact.Asset(it.name, it.downloadUrl) }
        if (assets.isEmpty()) return emptyList()
        return listOf(
            UpdateArtifact(
                component = UpdateComponent.RIME_CONFIG,
                version = release.tagName.removePrefix("v"),
                pageUrl = release.pageUrl,
                assets = assets
            )
        )
    }

    private val APP_APK = Regex("org\\.fcitx\\.fcitx5\\.android-[^-]+-.+-release\\.apk")
    private val RIME_PLUGIN_APK =
        Regex("org\\.fcitx\\.fcitx5\\.android\\.plugin\\.rime-[^-]+-.+-release\\.apk")
    private const val RIME_CONFIG_ARCHIVE = "rime-ice-t9-phone-main.zip"
}

object UpdateVersion {
    fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = candidate.numericParts()
        val currentParts = current.numericParts()
        val size = maxOf(candidateParts.size, currentParts.size)
        return (0 until size).firstNotNullOfOrNull { index ->
            val comparison = candidateParts.getOrElse(index) { 0 }
                .compareTo(currentParts.getOrElse(index) { 0 })
            comparison.takeIf { it != 0 }
        }?.let { it > 0 } ?: false
    }

    private fun String.numericParts(): List<Int> =
        removePrefix("v")
            .substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
}

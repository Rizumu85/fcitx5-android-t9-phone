/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class UpdateRelease(
    val version: String,
    val pageUrl: String,
    val appAssets: List<Asset>,
    val rimeAssets: List<Asset>
) {
    data class Asset(
        val name: String,
        val downloadUrl: String
    )
}

enum class UpdateComponent {
    APP,
    RIME
}

data class InstalledUpdateVersions(
    val app: String,
    val rime: String?
)

object UpdatePlan {
    fun availableComponents(
        release: UpdateRelease,
        installed: InstalledUpdateVersions
    ): Set<UpdateComponent> = buildSet {
        if (release.appAssets.isNotEmpty() && UpdateVersion.isNewer(release.version, installed.app)) {
            add(UpdateComponent.APP)
        }
        if (release.rimeAssets.isNotEmpty() &&
            (installed.rime == null || UpdateVersion.isNewer(release.version, installed.rime))
        ) {
            add(UpdateComponent.RIME)
        }
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

    fun parse(payload: String): UpdateRelease {
        val release = json.decodeFromString<GithubRelease>(payload)
        val assets = release.assets.map { UpdateRelease.Asset(it.name, it.downloadUrl) }
        return UpdateRelease(
            version = release.tagName.removePrefix("v"),
            pageUrl = release.pageUrl,
            appAssets = assets.filter { it.name.matches(APP_APK) },
            rimeAssets = assets.filter { it.name.matches(RIME_APK) }
        )
    }

    private val APP_APK = Regex("org\\.fcitx\\.fcitx5\\.android-[^-]+-.+-release\\.apk")
    private val RIME_APK =
        Regex("org\\.fcitx\\.fcitx5\\.android\\.plugin\\.rime-[^-]+-.+-release\\.apk")
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

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core.data

import kotlinx.serialization.Serializable

@Serializable
data class PluginPackageIdentity(
    val packageName: String,
    val versionCode: Long,
    val lastUpdateTime: Long
)

@Serializable
data class InstalledPluginState(
    val packageName: String,
    val apiVersion: String,
    val domain: String?,
    val description: String,
    val hasService: Boolean,
    val versionName: String,
    val nativeLibraryDir: String
) {
    fun toDescriptor() = PluginDescriptor(
        packageName = packageName,
        apiVersion = apiVersion,
        domain = domain,
        description = description,
        hasService = hasService,
        versionName = versionName,
        nativeLibraryDir = nativeLibraryDir
    )

    companion object {
        fun from(descriptor: PluginDescriptor) = InstalledPluginState(
            packageName = descriptor.packageName,
            apiVersion = descriptor.apiVersion,
            domain = descriptor.domain,
            description = descriptor.description,
            hasService = descriptor.hasService,
            versionName = descriptor.versionName,
            nativeLibraryDir = descriptor.nativeLibraryDir
        )
    }
}

@Serializable
data class DataInstallationState(
    val formatVersion: Int = CurrentFormatVersion,
    val mainDescriptorSha256: SHA256,
    val mergedDescriptorSha256: SHA256,
    val pluginPackages: List<PluginPackageIdentity>,
    val loadedPlugins: List<InstalledPluginState>
) {
    fun canReuse(
        mainDescriptorSha256: SHA256,
        mergedDescriptorSha256: SHA256,
        pluginPackages: List<PluginPackageIdentity>
    ): Boolean {
        val canonicalPackages = pluginPackages.canonicalOrder()
        return formatVersion == CurrentFormatVersion &&
            this.mainDescriptorSha256 == mainDescriptorSha256 &&
            this.mergedDescriptorSha256 == mergedDescriptorSha256 &&
            this.pluginPackages == canonicalPackages &&
            loadedPlugins.map { it.packageName } == canonicalPackages.map { it.packageName }
    }

    fun restoredPlugins(): Set<PluginDescriptor> =
        loadedPlugins.mapTo(linkedSetOf()) { it.toDescriptor() }

    companion object {
        const val CurrentFormatVersion = 1

        fun completed(
            mainDescriptorSha256: SHA256,
            mergedDescriptorSha256: SHA256,
            pluginPackages: List<PluginPackageIdentity>,
            loadedPlugins: Set<PluginDescriptor>
        ) = DataInstallationState(
            mainDescriptorSha256 = mainDescriptorSha256,
            mergedDescriptorSha256 = mergedDescriptorSha256,
            pluginPackages = pluginPackages.canonicalOrder(),
            loadedPlugins = loadedPlugins
                .sortedBy { it.packageName }
                .map(InstalledPluginState::from)
        )
    }
}

fun List<PluginPackageIdentity>.canonicalOrder(): List<PluginPackageIdentity> =
    distinctBy { it.packageName }.sortedBy { it.packageName }

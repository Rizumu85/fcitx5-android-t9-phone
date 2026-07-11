/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core.data

data class PluginPackageIdentity(
    val packageName: String,
    val versionCode: Long,
    val lastUpdateTime: Long
)

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

data class DataInstallationState(
    val mainDescriptorSha256: SHA256,
    val mergedDescriptorFileSha256: SHA256,
    val pluginPackages: List<PluginPackageIdentity>,
    val loadedPlugins: List<InstalledPluginState>
) {
    fun canReuse(
        mainDescriptorSha256: SHA256,
        mergedDescriptorFileSha256: SHA256,
        pluginPackages: List<PluginPackageIdentity>
    ): Boolean {
        val canonicalPackages = pluginPackages.canonicalOrder()
        return this.mainDescriptorSha256 == mainDescriptorSha256 &&
            this.mergedDescriptorFileSha256 == mergedDescriptorFileSha256 &&
            this.pluginPackages == canonicalPackages &&
            loadedPlugins.map { it.packageName } == canonicalPackages.map { it.packageName }
    }

    fun restoredPlugins(): Set<PluginDescriptor> =
        loadedPlugins.mapTo(linkedSetOf()) { it.toDescriptor() }

    companion object {
        fun completed(
            mainDescriptorSha256: SHA256,
            mergedDescriptorFileSha256: SHA256,
            pluginPackages: List<PluginPackageIdentity>,
            loadedPlugins: Set<PluginDescriptor>
        ) = DataInstallationState(
            mainDescriptorSha256 = mainDescriptorSha256,
            mergedDescriptorFileSha256 = mergedDescriptorFileSha256,
            pluginPackages = pluginPackages.canonicalOrder(),
            loadedPlugins = loadedPlugins
                .sortedBy { it.packageName }
                .map(InstalledPluginState::from)
        )
    }
}

fun List<PluginPackageIdentity>.canonicalOrder(): List<PluginPackageIdentity> =
    distinctBy { it.packageName }.sortedBy { it.packageName }

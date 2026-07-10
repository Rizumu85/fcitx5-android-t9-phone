/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataInstallationStateTest {

    private val pluginIdentity = PluginPackageIdentity(
        packageName = "org.fcitx.fcitx5.android.plugin.rime.debug",
        versionCode = 12L,
        lastUpdateTime = 34L
    )

    private val plugin = PluginDescriptor(
        packageName = pluginIdentity.packageName,
        apiVersion = PluginDescriptor.pluginAPI,
        domain = "fcitx5-rime",
        description = "Rime",
        hasService = false,
        versionName = "1.0",
        nativeLibraryDir = "/native"
    )

    @Test
    fun completedStateCanonicalizesPackagesAndRestoresPlugins() {
        val duplicate = pluginIdentity.copy(versionCode = 99L)

        val state = DataInstallationState.completed(
            mainDescriptorSha256 = "main",
            mergedDescriptorSha256 = "merged",
            pluginPackages = listOf(pluginIdentity, duplicate),
            loadedPlugins = setOf(plugin)
        )

        assertEquals(listOf(pluginIdentity), state.pluginPackages)
        assertEquals(setOf(plugin), state.restoredPlugins())
    }

    @Test
    fun unchangedCompletedFingerprintCanBeReused() {
        val state = DataInstallationState.completed(
            mainDescriptorSha256 = "main",
            mergedDescriptorSha256 = "merged",
            pluginPackages = listOf(pluginIdentity),
            loadedPlugins = setOf(plugin)
        )

        assertTrue(
            state.canReuse(
                mainDescriptorSha256 = "main",
                mergedDescriptorSha256 = "merged",
                pluginPackages = listOf(pluginIdentity)
            )
        )
    }

    @Test
    fun appPluginAndMergedDescriptorChangesInvalidateFastPath() {
        val state = DataInstallationState.completed(
            mainDescriptorSha256 = "main",
            mergedDescriptorSha256 = "merged",
            pluginPackages = listOf(pluginIdentity),
            loadedPlugins = setOf(plugin)
        )

        assertFalse(state.canReuse("new-main", "merged", listOf(pluginIdentity)))
        assertFalse(state.canReuse("main", "new-merged", listOf(pluginIdentity)))
        assertFalse(
            state.canReuse(
                "main",
                "merged",
                listOf(pluginIdentity.copy(lastUpdateTime = 35L))
            )
        )
    }

    @Test
    fun oldStateFormatCannotBeReused() {
        val state = DataInstallationState(
            formatVersion = 0,
            mainDescriptorSha256 = "main",
            mergedDescriptorSha256 = "merged",
            pluginPackages = listOf(pluginIdentity),
            loadedPlugins = listOf(InstalledPluginState.from(plugin))
        )

        assertFalse(state.canReuse("main", "merged", listOf(pluginIdentity)))
    }

    @Test
    fun incompletePluginOutcomeCannotBeReused() {
        val state = DataInstallationState(
            mainDescriptorSha256 = "main",
            mergedDescriptorSha256 = "merged",
            pluginPackages = listOf(pluginIdentity),
            loadedPlugins = emptyList()
        )

        assertFalse(state.canReuse("main", "merged", listOf(pluginIdentity)))
    }
}

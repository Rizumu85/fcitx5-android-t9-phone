/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class DataInstallationStateTest {

    private val mainFingerprint = "a".repeat(64)
    private val mergedFileFingerprint = "b".repeat(64)

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
            mainDescriptorSha256 = mainFingerprint,
            mergedDescriptorFileSha256 = mergedFileFingerprint,
            pluginPackages = listOf(pluginIdentity, duplicate),
            loadedPlugins = setOf(plugin)
        )

        assertEquals(listOf(pluginIdentity), state.pluginPackages)
        assertEquals(setOf(plugin), state.restoredPlugins())
    }

    @Test
    fun unchangedCompletedFingerprintCanBeReused() {
        val state = DataInstallationState.completed(
            mainDescriptorSha256 = mainFingerprint,
            mergedDescriptorFileSha256 = mergedFileFingerprint,
            pluginPackages = listOf(pluginIdentity),
            loadedPlugins = setOf(plugin)
        )

        assertTrue(
            state.canReuse(
                mainDescriptorSha256 = mainFingerprint,
                mergedDescriptorFileSha256 = mergedFileFingerprint,
                pluginPackages = listOf(pluginIdentity)
            )
        )
    }

    @Test
    fun appPluginAndMergedDescriptorChangesInvalidateFastPath() {
        val state = DataInstallationState.completed(
            mainDescriptorSha256 = mainFingerprint,
            mergedDescriptorFileSha256 = mergedFileFingerprint,
            pluginPackages = listOf(pluginIdentity),
            loadedPlugins = setOf(plugin)
        )

        assertFalse(
            state.canReuse("c".repeat(64), mergedFileFingerprint, listOf(pluginIdentity))
        )
        assertFalse(
            state.canReuse(mainFingerprint, "d".repeat(64), listOf(pluginIdentity))
        )
        assertFalse(
            state.canReuse(
                mainFingerprint,
                mergedFileFingerprint,
                listOf(pluginIdentity.copy(lastUpdateTime = 35L))
            )
        )
    }

    @Test
    fun binaryCodecRoundTripsCompletedProof() {
        val state = completedState()

        assertEquals(
            state,
            DataInstallationStateCodec.decode(DataInstallationStateCodec.encode(state))
        )
    }

    @Test
    fun binaryCodecRejectsOldCorruptTruncatedAndJsonRecords() {
        val encoded = DataInstallationStateCodec.encode(completedState())
        val oldVersion = encoded.copyOf().also { bytes ->
            ByteBuffer.wrap(bytes).putInt(Int.SIZE_BYTES, 2)
        }
        val corruptChecksum = encoded.copyOf().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        }

        assertNull(DataInstallationStateCodec.decode(oldVersion))
        assertNull(DataInstallationStateCodec.decode(corruptChecksum))
        assertNull(DataInstallationStateCodec.decode(encoded.copyOf(encoded.size - 1)))
        assertNull(DataInstallationStateCodec.decode(encoded + 0))
        assertNull(DataInstallationStateCodec.decode("{}".toByteArray()))
    }

    @Test
    fun incompletePluginOutcomeCannotBeReused() {
        val state = DataInstallationState(
            mainDescriptorSha256 = mainFingerprint,
            mergedDescriptorFileSha256 = mergedFileFingerprint,
            pluginPackages = listOf(pluginIdentity),
            loadedPlugins = emptyList()
        )

        assertFalse(
            state.canReuse(mainFingerprint, mergedFileFingerprint, listOf(pluginIdentity))
        )
    }

    private fun completedState() = DataInstallationState.completed(
        mainDescriptorSha256 = mainFingerprint,
        mergedDescriptorFileSha256 = mergedFileFingerprint,
        pluginPackages = listOf(pluginIdentity),
        loadedPlugins = setOf(plugin)
    )
}

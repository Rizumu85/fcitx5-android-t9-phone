/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

internal object DataInstallationStateCodec {
    fun encode(state: DataInstallationState): ByteArray {
        val payload = encodePayload(state)
        require(payload.size <= MaxPayloadBytes) { "Installation state is too large" }
        return ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(Magic)
                data.writeInt(CurrentFormatVersion)
                data.writeInt(payload.size)
                data.write(payload)
                data.writeLong(payload.checksum())
            }
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): DataInstallationState? {
        if (bytes.size !in HeaderBytes..MaxEncodedBytes) return null
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                require(data.readInt() == Magic)
                require(data.readInt() == CurrentFormatVersion)
                val payloadSize = data.readInt().also { require(it in 0..MaxPayloadBytes) }
                val payload = ByteArray(payloadSize).also(data::readFully)
                val checksum = data.readLong()
                require(data.read() == -1)
                // AtomicFile protects interrupted writes; the checksum also rejects valid-looking
                // field damage instead of restoring corrupted plugin metadata.
                require(payload.checksum() == checksum)
                decodePayload(payload)
            }
        }.getOrNull()
    }

    private fun encodePayload(state: DataInstallationState): ByteArray =
        ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeFingerprint(state.mainDescriptorSha256)
                data.writeFingerprint(state.mergedDescriptorFileSha256)
                data.writeRecordCount(state.pluginPackages.size)
                state.pluginPackages.forEach { plugin ->
                    data.writeUTF(plugin.packageName)
                    data.writeLong(plugin.versionCode)
                    data.writeLong(plugin.lastUpdateTime)
                }
                data.writeRecordCount(state.loadedPlugins.size)
                state.loadedPlugins.forEach { plugin ->
                    data.writeUTF(plugin.packageName)
                    data.writeUTF(plugin.apiVersion)
                    data.writeNullableUTF(plugin.domain)
                    data.writeUTF(plugin.description)
                    data.writeBoolean(plugin.hasService)
                    data.writeUTF(plugin.versionName)
                    data.writeUTF(plugin.nativeLibraryDir)
                }
            }
        }.toByteArray()

    private fun decodePayload(payload: ByteArray): DataInstallationState =
        DataInputStream(ByteArrayInputStream(payload)).use { data ->
            val mainDescriptorSha256 = data.readFingerprint()
            val mergedDescriptorFileSha256 = data.readFingerprint()
            val pluginPackages = List(data.readRecordCount()) {
                PluginPackageIdentity(
                    packageName = data.readUTF(),
                    versionCode = data.readLong(),
                    lastUpdateTime = data.readLong()
                )
            }
            val loadedPlugins = List(data.readRecordCount()) {
                InstalledPluginState(
                    packageName = data.readUTF(),
                    apiVersion = data.readUTF(),
                    domain = data.readNullableUTF(),
                    description = data.readUTF(),
                    hasService = data.readBoolean(),
                    versionName = data.readUTF(),
                    nativeLibraryDir = data.readUTF()
                )
            }
            // Unknown trailing fields represent a different format, not a compatible extension.
            require(data.read() == -1)
            DataInstallationState(
                mainDescriptorSha256 = mainDescriptorSha256,
                mergedDescriptorFileSha256 = mergedDescriptorFileSha256,
                pluginPackages = pluginPackages,
                loadedPlugins = loadedPlugins
            )
        }

    private fun DataOutputStream.writeFingerprint(value: SHA256) {
        require(value.isLowerHexFingerprint())
        writeUTF(value)
    }

    private fun DataInputStream.readFingerprint(): SHA256 =
        readUTF().also { require(it.isLowerHexFingerprint()) }

    private fun DataOutputStream.writeRecordCount(value: Int) {
        require(value in 0..MaxRecords)
        writeInt(value)
    }

    private fun DataInputStream.readRecordCount(): Int =
        readInt().also { require(it in 0..MaxRecords) }

    private fun DataOutputStream.writeNullableUTF(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullableUTF(): String? =
        if (readBoolean()) readUTF() else null

    private fun String.isLowerHexFingerprint(): Boolean =
        length == FingerprintLength && all { it in '0'..'9' || it in 'a'..'f' }

    private fun ByteArray.checksum(): Long = CRC32().also { it.update(this) }.value

    private const val Magic = 0x54394449 // "T9DI"
    private const val CurrentFormatVersion = 3
    private const val FingerprintLength = 64
    private const val MaxRecords = 1024
    private const val MaxPayloadBytes = 1024 * 1024
    private const val HeaderBytes = Int.SIZE_BYTES * 3 + Long.SIZE_BYTES
    private const val MaxEncodedBytes = MaxPayloadBytes + HeaderBytes
}

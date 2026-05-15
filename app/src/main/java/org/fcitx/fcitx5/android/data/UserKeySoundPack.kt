/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.utils.appContext
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

object UserKeySoundPack {

    data class Pack(
        val id: String,
        val name: String,
        val isDefault: Boolean
    )

    enum class Sample(val fileName: String) {
        Standard("standard.ogg"),
        Space("space.ogg"),
        Delete("delete.ogg")
    }

    private const val PREF_ACTIVE_ID = "key_sound_pack_active_id"
    private const val PREF_VERSION = "key_sound_pack_version"
    private const val DEFAULT_PACK_ID = "android-system"
    private const val LOCAL_FILE_HEADER = 0x04034b50
    private const val GENERAL_PURPOSE_ENCRYPTED = 0x0001
    private const val PACK_NAME_FILE = "name.txt"

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(appContext) }
    private val packsDir: File
        get() = File(appContext.filesDir, "key-sounds/packs")
    private val activePackDir: File?
        get() {
            val id = activePackId
            return if (id == DEFAULT_PACK_ID) null else File(packsDir, id)
        }

    val activePackId: String
        get() = prefs.getString(PREF_ACTIVE_ID, DEFAULT_PACK_ID).orEmpty()
            .ifBlank { DEFAULT_PACK_ID }

    val version: Long
        get() = prefs.getLong(PREF_VERSION, 0L)

    val usesBuiltInDefaultSounds: Boolean
        get() = activePackId == DEFAULT_PACK_ID

    fun sampleFile(sample: Sample): File = File(activePackDir ?: File(""), sample.fileName)

    fun hasUsablePack(id: String = activePackId): Boolean {
        if (id == DEFAULT_PACK_ID) return true
        return Sample.entries.all { File(File(packsDir, id), it.fileName).isFile }
    }

    fun displayNameOrDefault(context: Context): String {
        return activePack(context).name
    }

    fun activePack(context: Context): Pack {
        return listPacks(context).firstOrNull { it.id == activePackId }
            ?: defaultPack(context)
    }

    fun listPacks(context: Context): List<Pack> {
        val imported = packsDir.listFiles()
            ?.filter { it.isDirectory && hasUsablePack(it.name) }
            ?.map { dir ->
                Pack(
                    id = dir.name,
                    name = File(dir, PACK_NAME_FILE).takeIf { it.isFile }
                        ?.readText()
                        ?.trim()
                        ?.ifBlank { null }
                        ?: dir.name,
                    isDefault = false
                )
            }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            .orEmpty()
        return listOf(defaultPack(context)) + imported
    }

    fun suggestedName(fileName: String): String {
        return fileName.substringAfterLast('/').substringAfterLast('\\')
            .substringBeforeLast('.')
            .trim()
    }

    fun importPack(
        context: Context,
        name: String,
        uri: Uri,
        fileName: String
    ): Result<Unit> = runCatching {
        val sanitizedName = name.trim()
        require(sanitizedName.isNotEmpty()) {
            context.getString(R.string.key_sound_pack_name_empty)
        }

        val resolver = context.contentResolver
        val packBytes = resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { context.getString(R.string.key_sound_pack_open_failed) }
            input.readAllBytesCompat()
        }
        rejectEncryptedPack(context, packBytes)
        val samples = extractSamples(context, packBytes)

        val id = "pack-${System.currentTimeMillis()}"
        val tempDir = File(packsDir, "$id.tmp").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            File(tempDir, PACK_NAME_FILE).writeText(sanitizedName)
            samples.forEach { (sample, bytes) ->
                File(tempDir, sample.fileName).writeBytes(bytes)
            }
            File(tempDir, "source.bds").writeBytes(packBytes)

            packsDir.mkdirs()
            val targetDir = File(packsDir, id)
            check(tempDir.renameTo(targetDir)) {
                context.getString(R.string.key_sound_pack_save_failed)
            }
            prefs.edit {
                putString(PREF_ACTIVE_ID, id)
                putLong(PREF_VERSION, System.currentTimeMillis())
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun selectPack(id: String): Boolean {
        if (id != DEFAULT_PACK_ID && !hasUsablePack(id)) return false
        prefs.edit {
            putString(PREF_ACTIVE_ID, id)
            putLong(PREF_VERSION, System.currentTimeMillis())
        }
        return true
    }

    fun deletePack(id: String): Boolean {
        if (id == DEFAULT_PACK_ID) return false
        val deleted = File(packsDir, id).deleteRecursively()
        if (activePackId == id) {
            selectPack(DEFAULT_PACK_ID)
        } else if (deleted) {
            prefs.edit { putLong(PREF_VERSION, System.currentTimeMillis()) }
        }
        return deleted
    }

    fun renamePack(context: Context, id: String, name: String): Result<Unit> = runCatching {
        if (id == DEFAULT_PACK_ID || !hasUsablePack(id)) return@runCatching
        val sanitizedName = name.trim()
        require(sanitizedName.isNotEmpty()) {
            context.getString(R.string.key_sound_pack_name_empty)
        }
        File(File(packsDir, id), PACK_NAME_FILE).writeText(sanitizedName)
        prefs.edit {
            putLong(PREF_VERSION, System.currentTimeMillis())
        }
    }

    private fun defaultPack(context: Context): Pack {
        return Pack(
            id = DEFAULT_PACK_ID,
            name = context.getString(R.string.key_sound_pack_android_default),
            isDefault = true
        )
    }

    private fun rejectEncryptedPack(context: Context, bytes: ByteArray) {
        ByteArrayInputStream(bytes).buffered().use { stream ->
            while (true) {
                val signature = stream.readIntLeOrNull() ?: break
                if (signature != LOCAL_FILE_HEADER) break

                stream.skipFully(2)
                val flags = stream.readShortLe()
                stream.skipFully(18)
                val nameLength = stream.readShortLe()
                val extraLength = stream.readShortLe()
                stream.skipFully(nameLength.toLong() + extraLength.toLong())

                if ((flags and GENERAL_PURPOSE_ENCRYPTED) != 0) {
                    throw ZipException(context.getString(R.string.key_sound_pack_encrypted))
                }
            }
        }
    }

    private fun extractSamples(context: Context, bytes: ByteArray): Map<Sample, ByteArray> {
        val found = mutableMapOf<Sample, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                val sample = sampleForEntry(entry.name)
                if (sample != null && sample !in found) {
                    val sampleBytes = stream.readAllBytesCompat()
                    if (sampleBytes.isNotEmpty()) {
                        found[sample] = sampleBytes
                    }
                }
                stream.closeEntry()
            }
        }

        val missing = Sample.entries.filterNot { it in found }
        if (missing.isNotEmpty()) {
            throw ZipException(context.getString(R.string.key_sound_pack_missing_samples))
        }
        return found
    }

    private fun sampleForEntry(name: String): Sample? {
        val fileName = name.substringAfterLast('/').substringAfterLast('\\')
        if (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) != "ogg") {
            return null
        }
        val baseName = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
        return when (baseName) {
            "aj", "aj1" -> Sample.Standard
            "ajgn", "aj2" -> Sample.Space
            "ajhc", "aj3" -> Sample.Delete
            else -> null
        }
    }

    private fun InputStream.readIntLeOrNull(): Int? {
        val b0 = read()
        if (b0 < 0) return null
        val b1 = read()
        val b2 = read()
        val b3 = read()
        if (b1 < 0 || b2 < 0 || b3 < 0) throw EOFException()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun InputStream.readShortLe(): Int {
        val b0 = read()
        val b1 = read()
        if (b0 < 0 || b1 < 0) throw EOFException()
        return b0 or (b1 shl 8)
    }

    private fun InputStream.skipFully(byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else if (read() >= 0) {
                remaining--
            } else {
                throw EOFException()
            }
        }
    }

    private fun InputStream.readAllBytesCompat(): ByteArray {
        val output = ByteArrayOutputStream()
        copyTo(output)
        return output.toByteArray()
    }
}

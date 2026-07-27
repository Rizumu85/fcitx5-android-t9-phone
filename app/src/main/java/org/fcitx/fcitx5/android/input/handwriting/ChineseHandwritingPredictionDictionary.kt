/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.input.t9.ChineseT9OutputScript

internal fun interface ChineseHandwritingPredictionProvider {
    suspend fun predictionsAfter(
        context: String,
        script: ChineseT9OutputScript,
        limit: Int
    ): List<String>

    suspend fun preload() = Unit
}

internal class AssetChineseHandwritingPredictionProvider(
    context: Context
) : ChineseHandwritingPredictionProvider {
    private val applicationContext = context.applicationContext
    private val loadMutex = Mutex()

    @Volatile
    private var dictionary: ChineseHandwritingPredictionDictionary? = null

    override suspend fun predictionsAfter(
        context: String,
        script: ChineseT9OutputScript,
        limit: Int
    ): List<String> = withContext(Dispatchers.IO) {
        loadDictionary().predictionsAfter(context, script, limit)
    }

    override suspend fun preload() {
        withContext(Dispatchers.IO) {
            loadDictionary()
        }
    }

    private suspend fun loadDictionary(): ChineseHandwritingPredictionDictionary {
        dictionary?.let { return it }
        return loadMutex.withLock {
            dictionary?.let { return@withLock it }
            val cacheFile = File(
                File(applicationContext.cacheDir, CacheDirectory),
                CacheFileName
            )
            val loaded = runCatching { openCache(cacheFile) }
                .getOrElse {
                    cacheFile.delete()
                    extractCache(cacheFile)
                    openCache(cacheFile)
                }
            dictionary = loaded
            loaded
        }
    }

    private fun openCache(file: File): ChineseHandwritingPredictionDictionary {
        FileInputStream(file).channel.use { channel ->
            return ChineseHandwritingPredictionDictionary.open(
                channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size())
            )
        }
    }

    private fun extractCache(target: File) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.delete()
        applicationContext.assets.open(AssetPath).use { compressed ->
            GZIPInputStream(compressed).use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            error("Unable to install Chinese prediction cache")
        }
    }

    private companion object {
        // Do not use Android's special .gz suffix: aapt would expand and rename that asset,
        // defeating the versioned cache extraction performed here.
        const val AssetPath = "t9/chinese-predict-v1.cpz"
        const val CacheDirectory = "chinese-prediction"
        const val CacheFileName = "chinese-predict-v1.cpd"
    }
}

internal class ChineseHandwritingPredictionDictionary private constructor(
    source: ByteBuffer,
    private val traditional: Section,
    private val simplified: Section
) {
    private val buffer = source.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN)

    fun predictionsAfter(
        context: String,
        script: ChineseT9OutputScript,
        limit: Int
    ): List<String> {
        if (context.isBlank() || limit <= 0) return emptyList()
        val section = when (script) {
            ChineseT9OutputScript.Simplified -> simplified
            ChineseT9OutputScript.Traditional -> traditional
        }
        val query = context.toByteArray(Charsets.UTF_8)
        var lower = 0
        var upper = section.recordOffsets.lastIndex
        while (lower <= upper) {
            val middle = (lower + upper).ushr(1)
            val recordOffset = section.recordOffsets[middle]
            val comparison = compareRecordKey(recordOffset, query)
            when {
                comparison < 0 -> lower = middle + 1
                comparison > 0 -> upper = middle - 1
                else -> return readCandidates(recordOffset, limit)
            }
        }
        return emptyList()
    }

    private fun compareRecordKey(recordOffset: Int, query: ByteArray): Int {
        val keyLength = unsignedShort(recordOffset)
        val keyOffset = recordOffset + ShortBytes
        val sharedLength = minOf(keyLength, query.size)
        for (index in 0 until sharedLength) {
            val recordByte = buffer.get(keyOffset + index).toInt() and 0xFF
            val queryByte = query[index].toInt() and 0xFF
            if (recordByte != queryByte) return recordByte.compareTo(queryByte)
        }
        return keyLength.compareTo(query.size)
    }

    private fun readCandidates(recordOffset: Int, limit: Int): List<String> {
        val keyLength = unsignedShort(recordOffset)
        var cursor = recordOffset + ShortBytes + keyLength
        val candidateCount = buffer.get(cursor).toInt() and 0xFF
        cursor++
        return buildList(minOf(candidateCount, limit)) {
            repeat(candidateCount) {
                val length = unsignedShort(cursor)
                cursor += ShortBytes
                if (size < limit) add(decodeUtf8(cursor, length))
                cursor += length
            }
        }
    }

    private fun decodeUtf8(offset: Int, length: Int): String {
        val bytes = ByteArray(length)
        val duplicate = buffer.duplicate()
        duplicate.position(offset)
        duplicate.get(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun unsignedShort(offset: Int): Int =
        buffer.getShort(offset).toInt() and 0xFFFF

    private data class Section(val recordOffsets: IntArray)

    companion object {
        private const val Magic = 0x43504431
        private const val FormatVersion = 1
        private const val HeaderSize = 24
        private const val ShortBytes = 2
        private const val MaxRecordCount = 1_000_000

        fun open(source: ByteBuffer): ChineseHandwritingPredictionDictionary {
            val buffer = source.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN)
            require(buffer.limit() >= HeaderSize) { "Chinese prediction dictionary is truncated" }
            require(buffer.getInt(0) == Magic) { "Unknown Chinese prediction dictionary" }
            require(buffer.getInt(4) == FormatVersion) {
                "Unsupported Chinese prediction dictionary version"
            }
            val traditionalOffset = buffer.getInt(8)
            val traditionalCount = buffer.getInt(12)
            val simplifiedOffset = buffer.getInt(16)
            val simplifiedCount = buffer.getInt(20)
            require(traditionalOffset == HeaderSize) { "Invalid traditional section offset" }
            require(simplifiedOffset in traditionalOffset..buffer.limit()) {
                "Invalid simplified section offset"
            }

            val traditional = Section(
                scanOffsets(
                    buffer = buffer,
                    start = traditionalOffset,
                    end = simplifiedOffset,
                    count = traditionalCount
                )
            )
            val simplified = Section(
                scanOffsets(
                    buffer = buffer,
                    start = simplifiedOffset,
                    end = buffer.limit(),
                    count = simplifiedCount
                )
            )
            return ChineseHandwritingPredictionDictionary(buffer, traditional, simplified)
        }

        private fun scanOffsets(
            buffer: ByteBuffer,
            start: Int,
            end: Int,
            count: Int
        ): IntArray {
            require(count in 0..MaxRecordCount) { "Invalid Chinese prediction record count" }
            val offsets = IntArray(count)
            var cursor = start
            repeat(count) { index ->
                offsets[index] = cursor
                cursor = skipRecord(buffer, cursor, end)
            }
            require(cursor == end) { "Chinese prediction section size does not match its index" }
            return offsets
        }

        private fun skipRecord(buffer: ByteBuffer, offset: Int, end: Int): Int {
            require(offset + ShortBytes <= end) { "Truncated Chinese prediction context" }
            val keyLength = buffer.getShort(offset).toInt() and 0xFFFF
            var cursor = offset + ShortBytes + keyLength
            require(cursor < end) { "Truncated Chinese prediction record" }
            val candidateCount = buffer.get(cursor).toInt() and 0xFF
            cursor++
            repeat(candidateCount) {
                require(cursor + ShortBytes <= end) { "Truncated Chinese prediction candidate" }
                val candidateLength = buffer.getShort(cursor).toInt() and 0xFFFF
                cursor += ShortBytes + candidateLength
                require(cursor <= end) { "Truncated Chinese prediction candidate text" }
            }
            return cursor
        }
    }
}

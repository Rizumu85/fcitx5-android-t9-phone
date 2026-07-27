/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import org.fcitx.fcitx5.android.input.t9.ChineseT9OutputScript
import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseHandwritingPredictionDictionaryTest {
    @Test
    fun exactContextUsesTheSelectedOutputScriptAndLimit() {
        val dictionary = dictionary(
            traditional = linkedMapOf(
                "你" to listOf("好", "們"),
                "後" to listOf("來")
            ),
            simplified = linkedMapOf(
                "你" to listOf("好", "们"),
                "后" to listOf("来", "面")
            )
        )

        assertEquals(
            listOf("好"),
            dictionary.predictionsAfter("你", ChineseT9OutputScript.Simplified, limit = 1)
        )
        assertEquals(
            listOf("好", "們"),
            dictionary.predictionsAfter("你", ChineseT9OutputScript.Traditional, limit = 10)
        )
        assertEquals(
            listOf("来", "面"),
            dictionary.predictionsAfter("后", ChineseT9OutputScript.Simplified, limit = 10)
        )
        assertEquals(
            emptyList<String>(),
            dictionary.predictionsAfter("后", ChineseT9OutputScript.Traditional, limit = 10)
        )
    }

    private fun dictionary(
        traditional: Map<String, List<String>>,
        simplified: Map<String, List<String>>
    ): ChineseHandwritingPredictionDictionary {
        val traditionalBytes = section(traditional)
        val simplifiedBytes = section(simplified)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(0x43504431)
            data.writeInt(1)
            data.writeInt(24)
            data.writeInt(traditional.size)
            data.writeInt(24 + traditionalBytes.size)
            data.writeInt(simplified.size)
            data.write(traditionalBytes)
            data.write(simplifiedBytes)
        }
        return ChineseHandwritingPredictionDictionary.open(ByteBuffer.wrap(output.toByteArray()))
    }

    private fun section(rows: Map<String, List<String>>): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            rows.forEach { (key, candidates) ->
                val keyBytes = key.toByteArray(Charsets.UTF_8)
                data.writeShort(keyBytes.size)
                data.write(keyBytes)
                data.writeByte(candidates.size)
                candidates.forEach { candidate ->
                    val candidateBytes = candidate.toByteArray(Charsets.UTF_8)
                    data.writeShort(candidateBytes.size)
                    data.write(candidateBytes)
                }
            }
        }
        return output.toByteArray()
    }
}

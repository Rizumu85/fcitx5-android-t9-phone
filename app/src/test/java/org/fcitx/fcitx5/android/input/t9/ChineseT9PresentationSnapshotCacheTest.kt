/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ChineseT9PresentationSnapshotCacheTest {

    @Test
    fun reusesStateForSameSnapshotKey() {
        val cache = ChineseT9PresentationSnapshotCache()
        val key = key(candidateComment = "ni")
        var builds = 0

        val first = cache.getOrBuild(key) {
            builds += 1
            state("ni")
        }
        val second = cache.getOrBuild(key) {
            builds += 1
            state("unused")
        }

        assertEquals(1, builds)
        assertSame(first, second)
        assertEquals("ni", second.topReading.toString())
    }

    @Test
    fun rebuildsWhenSnapshotKeyChanges() {
        val cache = ChineseT9PresentationSnapshotCache()
        var builds = 0

        val first = cache.getOrBuild(key(candidateComment = "ni")) {
            builds += 1
            state("ni")
        }
        val second = cache.getOrBuild(key(candidateComment = "hao")) {
            builds += 1
            state("hao")
        }

        assertEquals(2, builds)
        assertEquals("ni", first.topReading.toString())
        assertEquals("hao", second.topReading.toString())
    }

    @Test
    fun resetDropsCachedSnapshot() {
        val cache = ChineseT9PresentationSnapshotCache()
        val key = key(candidateComment = "ni")
        var builds = 0

        cache.getOrBuild(key) {
            builds += 1
            state("ni")
        }
        cache.reset()
        cache.getOrBuild(key) {
            builds += 1
            state("ni")
        }

        assertEquals(2, builds)
    }

    private fun key(
        candidateComment: String,
        inputPreedit: String = "",
        rawSequence: String = "64",
        currentSegment: String = "64"
    ): ChineseT9PresentationSnapshotKey =
        ChineseT9PresentationSnapshotKey(
            pendingPunctuationText = null,
            inputPreedit = inputPreedit,
            candidateComment = candidateComment,
            candidateCursorIndex = 0,
            rawSequence = rawSequence,
            digitSequence = rawSequence.filter { it in '2'..'9' },
            currentSegment = currentSegment,
            fullComposition = rawSequence,
            model = T9CompositionModel(
                unresolvedDigits = currentSegment,
                rawPreedit = rawSequence
            )
        )

    private fun state(text: String): T9PresentationState =
        T9PresentationState(
            topReading = FormattedText(
                strings = arrayOf(text),
                flags = intArrayOf(TextFormatFlag.NoFlag.flag),
                cursor = -1
            ),
            pinyinOptions = listOf(text)
        )
}

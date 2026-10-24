/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executor

class T9DictionaryPersistenceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun replacementIsVisibleImmediatelyAndQueuedWritesCoalesceToLatestSnapshot() {
        val file = temporaryFolder.newFile("learned.txt")
        val executor = ManualExecutor()
        val writes = mutableListOf<String>()
        val persistence = T9DictionaryPersistence(
            file = file,
            defaultValue = emptyList(),
            decode = { it },
            encode = { it.joinToString(",") },
            executor = executor,
            atomicWrite = { _, content -> writes += content }
        )

        persistence.replace(listOf("hello"))
        persistence.replace(listOf("hello", "world"))

        assertEquals(listOf("hello", "world"), persistence.snapshot())
        assertEquals(emptyList<String>(), writes)

        executor.runNext()

        assertEquals(listOf("hello,world"), writes)
    }

    @Test
    fun unreadableFileIsPreservedBeforeItCanBeOverwritten() {
        val file = temporaryFolder.newFile("phrases.json").apply { writeText("not json") }
        val persistence = T9DictionaryPersistence(
            file = file,
            defaultValue = emptyList(),
            decode = { lines -> ChineseT9CustomPhraseDictionary.decodePhrases(lines) },
            encode = { it.toString() },
            executor = ManualExecutor()
        )

        assertEquals(emptyList<ChineseT9CustomPhrase>(), persistence.snapshot())
        assertEquals("not json", java.io.File(file.parentFile, "phrases.json.unreadable").readText())
    }

    @Test
    fun oneMalformedPhraseDoesNotDiscardTheOthers() {
        val phrases = ChineseT9CustomPhraseDictionary.decodePhrases(
            listOf("""[{"text":"你好","pinyin":"ni hao"},{"text":"坏"},{"text":"世界","pinyin":"shi jie"}]""")
        )

        assertEquals(listOf("你好", "世界"), phrases.map { it.text })
    }

    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks += command
        }

        fun runNext() {
            tasks.removeFirst().run()
        }
    }
}

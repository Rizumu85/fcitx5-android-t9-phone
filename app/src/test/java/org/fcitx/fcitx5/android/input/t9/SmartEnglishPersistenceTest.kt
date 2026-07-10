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

class SmartEnglishPersistenceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun replacementIsVisibleImmediatelyAndQueuedWritesCoalesceToLatestSnapshot() {
        val file = temporaryFolder.newFile("learned.txt")
        val executor = ManualExecutor()
        val writes = mutableListOf<String>()
        val persistence = SmartEnglishPersistence(
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

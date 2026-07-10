/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

class NumberModeControllerTest {

    @Test
    fun equalsCommitsBeforeEditorReadAndPublishesResultLater() {
        val host = Host(editorText = "1+2")
        val controller = host.controller()

        assertTrue(controller.commitOperator("="))

        assertEquals(listOf("="), host.committed)
        assertEquals(0, host.editorReadCount)
        assertTrue(host.equalsChoices.isEmpty())

        host.drain()

        assertEquals(1, host.editorReadCount)
        assertEquals(listOf("=" to "3"), host.equalsChoices)
        assertTrue(controller.hasTransientPanel)
    }

    @Test
    fun committedOperatorSuffixIsExcludedFromExpression() {
        val host = Host(editorText = "2*(3+4)")
        val controller = host.controller()

        controller.commitOperator("=")
        host.drain()

        assertEquals(listOf("=" to "14"), host.equalsChoices)
    }

    @Test
    fun newerInputInvalidatesQueuedEvaluation() {
        val host = Host(editorText = "1+2")
        val controller = host.controller()

        controller.commitOperator("=")
        controller.invalidatePendingEvaluation()
        host.drain()

        assertEquals(listOf("="), host.committed)
        assertTrue(host.equalsChoices.isEmpty())
        assertFalse(controller.hasTransientPanel)
    }

    @Test
    fun panelDismissalInvalidatesQueuedEvaluation() {
        val host = Host(editorText = "1+2")
        val controller = host.controller()

        controller.commitOperator("=")
        controller.dismissTransientPanel()
        host.drain()

        assertTrue(host.equalsChoices.isEmpty())
        assertFalse(controller.hasTransientPanel)
    }

    @Test
    fun ordinaryOperatorHasNoDeferredEditorWork() {
        val host = Host(editorText = "1")
        val controller = host.controller()

        controller.commitOperator("+")
        host.drain()

        assertEquals(listOf("+"), host.committed)
        assertEquals(0, host.editorReadCount)
        assertTrue(host.equalsChoices.isEmpty())
    }

    @Test
    fun confirmedDeferredResultCommitsAndClosesPanel() {
        val host = Host(editorText = "1+2")
        val controller = host.controller()
        controller.commitOperator("=")
        host.drain()

        val result = controller.handleTransientPanelKeyDown(
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            action = KeyEvent.ACTION_DOWN,
            repeatCount = 0
        )

        assertTrue(result.handled)
        assertEquals(listOf("=", "3"), host.committed)
        assertFalse(controller.hasTransientPanel)
    }

    private class Host(
        private var editorText: String
    ) {
        val editorDispatcher = QueuedDispatcher()
        val evaluationDispatcher = QueuedDispatcher()
        val committed = mutableListOf<String>()
        val equalsChoices = mutableListOf<Pair<String, String>>()
        var editorReadCount = 0

        fun controller(): NumberModeController = NumberModeController(
            scope = CoroutineScope(SupervisorJob()),
            editorDispatcher = editorDispatcher,
            evaluationDispatcher = evaluationDispatcher,
            commitText = { text ->
                committed += text
                editorText += text
            },
            getTextBeforeCursor = {
                editorReadCount += 1
                editorText
            },
            showOperatorHints = {},
            hideOperatorHints = {},
            showEqualsChoice = { prefix, result -> equalsChoices += prefix to result },
            hideEqualsChoice = {}
        )

        fun drain() {
            while (editorDispatcher.hasTasks || evaluationDispatcher.hasTasks) {
                editorDispatcher.runAll()
                evaluationDispatcher.runAll()
            }
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        val hasTasks: Boolean
            get() = tasks.isNotEmpty()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks += block
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().run()
            }
        }
    }
}

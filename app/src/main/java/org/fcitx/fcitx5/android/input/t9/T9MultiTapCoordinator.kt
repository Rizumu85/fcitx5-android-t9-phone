/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class T9MultiTapCoordinator(
    private val session: T9MultiTapSession = T9MultiTapSession(),
    private val timeoutScheduler: TimeoutScheduler = HandlerTimeoutScheduler(),
    private val nowMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val commitText: (String) -> Unit,
    private val setComposingText: (String) -> Unit,
    private val finishComposingText: () -> Unit,
    private val applyCase: (Char) -> Char,
    private val consumeShiftOnce: () -> Unit,
    private val recordLearningChar: (Char) -> Unit
) {
    interface TimeoutScheduler {
        fun cancel()
        fun schedule(delayMillis: Long, action: () -> Unit)
    }

    class HandlerTimeoutScheduler : TimeoutScheduler {
        private val handler = Handler(Looper.getMainLooper())
        private var pendingRunnable: Runnable? = null

        override fun cancel() {
            pendingRunnable?.let(handler::removeCallbacks)
            pendingRunnable = null
        }

        override fun schedule(delayMillis: Long, action: () -> Unit) {
            cancel()
            val runnable = Runnable {
                pendingRunnable = null
                action()
            }
            pendingRunnable = runnable
            handler.postDelayed(runnable, delayMillis)
        }
    }

    val hasPendingChar: Boolean
        get() = session.hasPendingChar

    fun pendingDisplayText(): String? =
        session.pendingChar()?.let { applyCase(it).toString() }

    fun handleKey(keyCode: Int): Boolean {
        val result = session.handleKey(keyCode, nowMillis()) ?: return false
        timeoutScheduler.cancel()
        result.committedPrevious?.let(::commitLetter)
        setComposingText(applyCase(result.pendingChar).toString())
        timeoutScheduler.schedule(T9MultiTapSession.DefaultTimeoutMillis) {
            commitPending()
        }
        return true
    }

    fun commitPending(): Boolean {
        timeoutScheduler.cancel()
        val pendingChar = session.commitPending() ?: return false
        commitLetter(pendingChar)
        return true
    }

    fun cancelPending(): Boolean {
        timeoutScheduler.cancel()
        if (!session.cancelPending()) return false
        // Android keeps the composing span unless both the text and the span are explicitly cleared.
        setComposingText("")
        finishComposingText()
        return true
    }

    fun reset(): Boolean {
        timeoutScheduler.cancel()
        if (!session.reset()) return false
        finishComposingText()
        return true
    }

    private fun commitLetter(letter: Char) {
        val char = applyCase(letter)
        commitText(char.toString())
        recordLearningChar(char)
        consumeShiftOnce()
    }
}

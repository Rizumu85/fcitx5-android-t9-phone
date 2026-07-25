/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class RimeSchemaSelectionSession(
    private val retryDelaysMs: List<Long> = DefaultRetryDelaysMs
) {
    data class Attempt(
        val target: ChineseT9Scheme,
        val generation: Long,
        val number: Int
    )

    sealed interface Failure {
        data object Stale : Failure
        data class Retry(
            val attempt: Attempt,
            val delayMs: Long
        ) : Failure
        data object Exhausted : Failure
    }

    var desiredTarget: ChineseT9Scheme? = null
        private set

    private var generation = 0L
    private var inFlight: Attempt? = null
    private var nextAttemptNumber = 0
    private var exhausted = false

    fun request(target: ChineseT9Scheme, engineReady: Boolean): Attempt? {
        if (desiredTarget != target) {
            generation += 1L
            desiredTarget = target
            inFlight = null
            nextAttemptNumber = 0
            exhausted = false
        }
        return issueAttemptIfReady(engineReady)
    }

    fun resume(fallbackTarget: ChineseT9Scheme): Attempt? {
        if (desiredTarget == null) {
            generation += 1L
            desiredTarget = fallbackTarget
            nextAttemptNumber = 0
            exhausted = false
        }
        return issueAttemptIfReady(engineReady = true)
    }

    fun suspendForEngineTransition() {
        // A Rime maintenance generation invalidates native call results, but not the scheme the
        // user requested while the engine was unavailable.
        generation += 1L
        inFlight = null
        nextAttemptNumber = 0
        exhausted = false
    }

    fun isCurrent(attempt: Attempt): Boolean =
        inFlight == attempt &&
            desiredTarget == attempt.target &&
            generation == attempt.generation

    fun onSuccess(attempt: Attempt): Boolean {
        if (!isCurrent(attempt)) return false
        generation += 1L
        desiredTarget = null
        inFlight = null
        nextAttemptNumber = 0
        exhausted = false
        return true
    }

    fun onFailure(attempt: Attempt): Failure {
        if (!isCurrent(attempt)) return Failure.Stale
        inFlight = null
        val delay = retryDelaysMs.getOrNull(attempt.number)
        if (delay == null) {
            exhausted = true
            return Failure.Exhausted
        }
        val retry = Attempt(
            target = attempt.target,
            generation = generation,
            number = attempt.number + 1
        )
        inFlight = retry
        nextAttemptNumber = retry.number + 1
        return Failure.Retry(retry, delay)
    }

    fun observeActive(scheme: ChineseT9Scheme): Boolean {
        if (desiredTarget != scheme) return false
        generation += 1L
        desiredTarget = null
        inFlight = null
        nextAttemptNumber = 0
        exhausted = false
        return true
    }

    private fun issueAttemptIfReady(engineReady: Boolean): Attempt? {
        if (!engineReady || exhausted || inFlight != null) return null
        val target = desiredTarget ?: return null
        return Attempt(target, generation, nextAttemptNumber).also {
            inFlight = it
            nextAttemptNumber += 1
        }
    }

    private companion object {
        val DefaultRetryDelaysMs = listOf(
            50L,
            100L,
            200L,
            400L,
            800L,
            1_600L,
            2_500L,
            2_500L
        )
    }
}

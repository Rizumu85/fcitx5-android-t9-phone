/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

class FcitxLifecycleRegistry : FcitxLifecycle {

    private val observers = ConcurrentLinkedQueue<FcitxLifecycleObserver>()

    override fun addObserver(observer: FcitxLifecycleObserver) {
        observers.add(observer)
    }

    override fun removeObserver(observer: FcitxLifecycleObserver) {
        observers.remove(observer)
    }

    override val currentState: FcitxLifecycle.State
        get() = internalState

    @Volatile
    private var internalState = FcitxLifecycle.State.STOPPED

    // The state enum is reassigned inside the critical section, so it cannot be its own lock.
    private val transitionLock = Any()

    override val lifecycleScope: CoroutineScope =
        FcitxLifecycleCoroutineScope(this).also { addObserver(it) }

    fun postEvent(event: FcitxLifecycle.Event) = synchronized(transitionLock) {
        when (event) {
            FcitxLifecycle.Event.ON_START -> {
                ensureAt(FcitxLifecycle.State.STOPPED)
                internalState = FcitxLifecycle.State.STARTING
            }
            FcitxLifecycle.Event.ON_READY -> {
                ensureAt(FcitxLifecycle.State.STARTING)
                internalState = FcitxLifecycle.State.READY
            }
            FcitxLifecycle.Event.ON_STOP -> {
                ensureAt(FcitxLifecycle.State.READY)
                internalState = FcitxLifecycle.State.STOPPING
            }
            FcitxLifecycle.Event.ON_STOPPED -> {
                ensureAt(FcitxLifecycle.State.STOPPING)
                internalState = FcitxLifecycle.State.STOPPED
            }
        }
        observers.forEach { it.onStateChanged(event) }
    }

    private fun ensureAt(state: FcitxLifecycle.State) = takeIf { (currentState == state) }
        ?: throw IllegalStateException("Currently not at $state!")

}

interface FcitxLifecycle {
    val currentState: State
    val lifecycleScope: CoroutineScope

    fun addObserver(observer: FcitxLifecycleObserver)
    fun removeObserver(observer: FcitxLifecycleObserver)

    enum class State {
        STARTING,
        READY,
        STOPPING,
        STOPPED
    }

    enum class Event {
        ON_START,
        ON_READY,
        ON_STOP,
        ON_STOPPED
    }

}

interface FcitxLifecycleOwner {
    val lifecycle: FcitxLifecycle
}

val FcitxLifecycleOwner.lifeCycleScope
    get() = lifecycle.lifecycleScope

fun interface FcitxLifecycleObserver {
    fun onStateChanged(event: FcitxLifecycle.Event)
}

class FcitxLifecycleCoroutineScope(
    val lifecycle: FcitxLifecycle,
    override val coroutineContext: CoroutineContext = SupervisorJob()
) : CoroutineScope, FcitxLifecycleObserver {
    override fun onStateChanged(event: FcitxLifecycle.Event) {
        if (lifecycle.currentState >= FcitxLifecycle.State.STOPPING) {
            coroutineContext.cancelChildren()
        }
    }
}

suspend fun <T> FcitxLifecycle.whenAtState(
    state: FcitxLifecycle.State,
    block: suspend CoroutineScope.() -> T
): T =
    if (state == currentState) block(lifecycleScope)
    else AtStateHelper(this, state).run(block)

suspend inline fun <T> FcitxLifecycle.whenReady(noinline block: suspend CoroutineScope.() -> T) =
    whenAtState(FcitxLifecycle.State.READY, block)

suspend inline fun <T> FcitxLifecycle.whenStopped(noinline block: suspend CoroutineScope.() -> T) =
    whenAtState(FcitxLifecycle.State.STOPPED, block)

fun <T> FcitxLifecycle.launchWhenReady(block: suspend CoroutineScope.() -> T) =
    lifecycleScope.launch { whenReady(block) }

fun <T> FcitxLifecycle.launchWhenStopped(block: suspend CoroutineScope.() -> T) =
    lifecycleScope.launch { whenStopped(block) }

private class AtStateHelper(val lifecycle: FcitxLifecycle, val state: FcitxLifecycle.State) {
    suspend fun <T> run(block: suspend CoroutineScope.() -> T): T {
        suspendCancellableCoroutine { continuation ->
            val resumed = AtomicBoolean(false)
            lateinit var observer: FcitxLifecycleObserver
            fun resumeIfAtState() {
                if (lifecycle.currentState == state && resumed.compareAndSet(false, true)) {
                    lifecycle.removeObserver(observer)
                    continuation.resume(Unit)
                }
            }
            observer = FcitxLifecycleObserver { resumeIfAtState() }
            lifecycle.addObserver(observer)
            continuation.invokeOnCancellation { lifecycle.removeObserver(observer) }
            // The transition may have happened after the caller's state check but before the
            // observer was registered. Missing it would leave this job, and every Fcitx job queued
            // behind it, suspended until the service is recreated.
            resumeIfAtState()
        }
        return block(lifecycle.lifecycleScope)
    }
}


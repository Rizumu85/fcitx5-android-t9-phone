/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

internal class FcitxInputContextGenerationSession {
    data class Binding(
        val uid: Int,
        val packageName: String
    )

    class RestoreRequest internal constructor(
        val generation: Long,
        val binding: Binding
    )

    class ActivationRequest internal constructor(
        val generation: Long,
        val binding: Binding
    )

    private var generation = 0L
    private var binding: Binding? = null
    private var restoredGeneration = NoGeneration

    fun bind(uid: Int, packageName: String): Binding {
        val next = Binding(uid, packageName)
        if (binding != next) {
            binding = next
            restoredGeneration = NoGeneration
        }
        return next
    }

    fun unbind() {
        binding = null
        restoredGeneration = NoGeneration
    }

    fun onFcitxReady(): RestoreRequest? {
        generation += 1L
        return binding?.let { RestoreRequest(generation, it) }
    }

    fun beginActivation(binding: Binding): ActivationRequest? =
        binding.takeIf { this.binding == it }?.let {
            ActivationRequest(generation, it)
        }

    fun completeActivation(request: ActivationRequest): Boolean {
        if (request.generation == generation && request.binding == binding) {
            restoredGeneration = generation
            return true
        }
        return false
    }

    fun isRestoreRequired(request: RestoreRequest): Boolean =
        request.generation == generation &&
            request.binding == binding &&
            restoredGeneration != generation

    fun completeRestore(request: RestoreRequest): Boolean {
        if (!isRestoreRequired(request)) return false
        restoredGeneration = generation
        return true
    }

    private companion object {
        const val NoGeneration = -1L
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChineseT9EngineOperation<Engine>(
    private val submit: (suspend Engine.() -> Unit) -> Unit,
    private val ownerDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    fun enqueue(execute: suspend Engine.() -> Unit) {
        submit(execute)
    }

    fun <Result> enqueue(
        acceptBefore: () -> Boolean,
        execute: suspend Engine.() -> Result,
        acceptAfter: (Result) -> Boolean = { true },
        finish: (Result) -> Unit
    ) {
        submit {
            if (!withContext(ownerDispatcher) { acceptBefore() }) return@submit
            val result = execute()
            withContext(ownerDispatcher) {
                if (acceptAfter(result)) finish(result)
            }
        }
    }
}

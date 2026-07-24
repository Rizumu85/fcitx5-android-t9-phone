/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

data class ChineseT9InputReceipt(
    val compositionTicket: ChineseT9CompositionTicket,
    val traceInputId: Long?
)

sealed interface ChineseT9KeyCommand<out KeyStroke> {
    val receipt: ChineseT9InputReceipt

    data class Stroke<KeyStroke>(
        val stroke: KeyStroke,
        override val receipt: ChineseT9InputReceipt
    ) : ChineseT9KeyCommand<KeyStroke>

    data class Press<KeyStroke>(
        val down: KeyStroke,
        val up: KeyStroke,
        override val receipt: ChineseT9InputReceipt
    ) : ChineseT9KeyCommand<KeyStroke>
}

class ChineseT9KeyInputSession<Engine, KeyStroke>(
    private val enqueueEngineOperation: (suspend Engine.() -> Unit) -> Unit,
    private val dispatchKeyStroke: suspend Engine.(KeyStroke) -> Unit,
    private val onDispatchStarted: (ChineseT9InputReceipt) -> Unit = {},
    private val onDispatchCompleted: (ChineseT9InputReceipt) -> Unit = {}
) {
    fun submit(command: ChineseT9KeyCommand<KeyStroke>) {
        // Input commands stay lossless and ordered in the existing engine operation lane. Only
        // presentation generations may be conflated; combining commands here would change text.
        enqueueEngineOperation {
            onDispatchStarted(command.receipt)
            try {
                when (command) {
                    is ChineseT9KeyCommand.Stroke -> dispatchKeyStroke(command.stroke)
                    is ChineseT9KeyCommand.Press -> {
                        dispatchKeyStroke(command.down)
                        dispatchKeyStroke(command.up)
                    }
                }
            } finally {
                onDispatchCompleted(command.receipt)
            }
        }
    }
}

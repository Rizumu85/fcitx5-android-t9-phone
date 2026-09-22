/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxAPI

class ChineseT9RimeBridge(private val io: RimeIo) {
    interface RimeIo {
        suspend fun getInput(): String
        suspend fun replaceInput(start: Int, length: Int, text: String, caretPos: Int): Boolean
    }

    suspend fun apply(projection: ChineseT9CompositionSession.EngineProjection): Boolean {
        val input = io.getInput()
        // The ordered command owns a complete projection, including every chosen reading.
        // No tail search, per-key replay, or callback may repair/mutate the local document.
        return io.replaceInput(0, input.length, projection.input, projection.input.length)
    }

    companion object {
        fun from(api: FcitxAPI): ChineseT9RimeBridge = ChineseT9RimeBridge(object : RimeIo {
            override suspend fun getInput(): String = api.getRimeInput()
            override suspend fun replaceInput(start: Int, length: Int, text: String, caretPos: Int): Boolean =
                api.replaceRimeInput(start, length, text, caretPos)
        })
    }
}

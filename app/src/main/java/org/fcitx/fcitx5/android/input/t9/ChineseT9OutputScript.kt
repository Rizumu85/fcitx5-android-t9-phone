/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class ChineseT9OutputScript(override val stringRes: Int) : ManagedPreferenceEnum {
    Simplified(R.string.chinese_t9_output_simplified),
    Traditional(R.string.chinese_t9_output_traditional)
}

data class ChineseT9RimeOption(
    val name: String,
    val enabled: Boolean
)

object ChineseT9OutputScriptPolicy {
    fun optionFor(
        scheme: ChineseT9Scheme,
        script: ChineseT9OutputScript
    ): ChineseT9RimeOption {
        val wantsSimplified = script == ChineseT9OutputScript.Simplified
        return when (scheme) {
            // The Stroke table starts from Traditional forms and converts toward Simplified,
            // while the phonetic schemas start from Simplified and convert toward Traditional.
            ChineseT9Scheme.STROKE -> ChineseT9RimeOption(
                name = "simplification",
                enabled = wantsSimplified
            )
            ChineseT9Scheme.PINYIN,
            ChineseT9Scheme.ZHUYIN -> ChineseT9RimeOption(
                name = "traditionalization",
                enabled = !wantsSimplified
            )
        }
    }
}

class ChineseT9OutputScriptSession {
    data class Request(
        val generation: Long,
        val scheme: ChineseT9Scheme,
        val option: ChineseT9RimeOption
    )

    private var generation = 0L
    private var activeScheme: ChineseT9Scheme? = null

    fun enterScheme(
        scheme: ChineseT9Scheme,
        script: ChineseT9OutputScript
    ): Request? {
        if (activeScheme == scheme) return null
        activeScheme = scheme
        return newRequest(scheme, script)
    }

    fun reapplyActiveScheme(
        scheme: ChineseT9Scheme,
        script: ChineseT9OutputScript
    ): Request? {
        if (activeScheme != scheme) return null
        return newRequest(scheme, script)
    }

    fun onRimeReady(
        scheme: ChineseT9Scheme,
        script: ChineseT9OutputScript
    ): Request {
        activeScheme = scheme
        return newRequest(scheme, script)
    }

    fun leaveRime() {
        activeScheme = null
        invalidate()
    }

    private fun newRequest(
        scheme: ChineseT9Scheme,
        script: ChineseT9OutputScript
    ): Request = Request(
        generation = ++generation,
        scheme = scheme,
        option = ChineseT9OutputScriptPolicy.optionFor(scheme, script)
    )

    fun invalidate() {
        generation++
    }

    fun isCurrent(request: Request, activeScheme: ChineseT9Scheme): Boolean =
        request.generation == generation &&
            request.scheme == activeScheme &&
            request.scheme == this.activeScheme
}

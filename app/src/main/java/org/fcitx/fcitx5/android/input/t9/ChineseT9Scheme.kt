/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import androidx.annotation.StringRes
import org.fcitx.fcitx5.android.R

enum class ChineseT9Scheme(
    @StringRes val compactLabelRes: Int,
    private val rimeSchemaId: String,
    private val rimeSubModeNames: Set<String>,
    val compositionDigits: IntRange,
    val supportsReadingFilter: Boolean
) {
    PINYIN(
        compactLabelRes = R.string.chinese_t9_pinyin_compact,
        rimeSchemaId = "t9",
        rimeSubModeNames = setOf("拼音九键", "拼音九鍵", "中文九键", "中文九鍵"),
        compositionDigits = 2..9,
        supportsReadingFilter = true
    ),
    STROKE(
        compactLabelRes = R.string.chinese_t9_stroke_compact,
        rimeSchemaId = "t9_stroke",
        // Old deployments can keep reporting the mistaken 五笔画 label until their Rime
        // configuration is updated; accepting it here does not expose that name in current UI.
        rimeSubModeNames = setOf(
            "笔画九键", "筆畫九鍵", "笔画", "筆畫",
            "五笔画九键", "五筆畫九鍵", "五笔画", "五筆畫"
        ),
        compositionDigits = 1..6,
        supportsReadingFilter = false
    ),
    ZHUYIN(
        compactLabelRes = R.string.chinese_t9_zhuyin_compact,
        rimeSchemaId = "t9_zhuyin",
        rimeSubModeNames = setOf("注音九键", "注音九鍵", "注音"),
        compositionDigits = 0..9,
        supportsReadingFilter = true
    );

    fun acceptsCompositionDigit(digit: Int): Boolean = digit in compositionDigits

    fun matchesRimeIdentity(identity: String): Boolean =
        identity.trim().let { it == rimeSchemaId || it in rimeSubModeNames }

    companion object {
        fun fromRimeIdentity(identity: String): ChineseT9Scheme =
            entries.firstOrNull { scheme -> scheme.matchesRimeIdentity(identity) }
                // Existing third-party Pinyin schema names must retain the old T9 behavior.
                ?: PINYIN
    }
}

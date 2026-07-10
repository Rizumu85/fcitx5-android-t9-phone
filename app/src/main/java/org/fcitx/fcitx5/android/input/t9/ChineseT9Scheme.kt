/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

enum class ChineseT9Scheme(
    val compactLabel: String,
    private val rimeSubModeNames: Set<String>,
    val compositionDigits: IntRange,
    val hasReadingFilterRow: Boolean
) {
    PINYIN(
        compactLabel = "拼音",
        rimeSubModeNames = setOf("拼音九键", "拼音九鍵", "中文九键", "中文九鍵"),
        compositionDigits = 2..9,
        hasReadingFilterRow = true
    ),
    STROKE(
        compactLabel = "五笔画",
        rimeSubModeNames = setOf("五笔画九键", "五筆畫九鍵", "五筆畫"),
        compositionDigits = 1..6,
        hasReadingFilterRow = false
    ),
    ZHUYIN(
        compactLabel = "注音",
        rimeSubModeNames = setOf("注音九键", "注音九鍵", "注音"),
        compositionDigits = 0..9,
        hasReadingFilterRow = true
    );

    fun acceptsCompositionDigit(digit: Int): Boolean = digit in compositionDigits

    fun matchesRimeSubMode(name: String): Boolean = name.trim() in rimeSubModeNames

    companion object {
        fun fromRimeSubMode(name: String): ChineseT9Scheme =
            entries.firstOrNull { scheme -> scheme.matchesRimeSubMode(name) }
                // Existing third-party Pinyin schema names must retain the old T9 behavior.
                ?: PINYIN
    }
}

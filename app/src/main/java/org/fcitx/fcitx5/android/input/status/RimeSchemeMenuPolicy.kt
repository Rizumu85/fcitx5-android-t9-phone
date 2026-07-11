/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

internal object RimeSchemeMenuPolicy {
    private val hiddenLabels = setOf(
        "latin mode",
        "英文模式",
        "雾凇拼音",
        "霧凇拼音"
    )

    private val pinyinLabels = setOf("拼音九键", "拼音九鍵", "中文九键", "中文九鍵")
    private val strokeLabels = setOf(
        "笔画九键", "筆畫九鍵", "五笔画九键", "五筆畫九鍵"
    )
    private val zhuyinLabels = setOf("注音九键", "注音九鍵")

    fun displayLabel(
        sourceLabel: String,
        pinyinLabel: String,
        strokeLabel: String,
        zhuyinLabel: String
    ): String? {
        val label = sourceLabel.trim()
        if (label.lowercase() in hiddenLabels) return null
        return when (label) {
            in pinyinLabels -> pinyinLabel
            in strokeLabels -> strokeLabel
            in zhuyinLabels -> zhuyinLabel
            else -> label.ifEmpty { null }
        }
    }
}

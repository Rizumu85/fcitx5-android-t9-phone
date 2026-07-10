/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class T9ZhuyinReadingFilterSession(
    private val resolver: T9ZhuyinResolver
) {
    private var optionCode = ""
    private var options = emptyList<String>()

    var selectedReading: String? = null
        private set

    fun updateRawCode(rawDigits: String) {
        optionCode = rawDigits
        options = resolver.readingOptions(rawDigits)
        selectedReading = null
    }

    fun visibleOptions(rawDigits: String): List<String> =
        options.takeIf { optionCode == rawDigits }.orEmpty()

    fun select(rawDigits: String, reading: String): Boolean {
        val normalized = T9ZhuyinResolver.normalizeCandidateReading(reading)
        if (normalized.isEmpty() || optionCode != rawDigits || normalized !in options) return false
        selectedReading = normalized
        return true
    }

    fun filterPrefixes(): List<String> = selectedReading?.let(::listOf).orEmpty()

    fun reset() {
        optionCode = ""
        options = emptyList()
        selectedReading = null
    }
}

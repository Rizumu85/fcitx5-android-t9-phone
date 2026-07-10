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

    var expanded: Boolean = false
        private set

    var selectedReading: String? = null
        private set

    fun canOpen(rawDigits: String): Boolean =
        rawDigits.isNotEmpty() && resolver.resolve(rawDigits) is T9ZhuyinResolver.Result.Valid

    fun open(rawDigits: String, preferredReading: String?): Boolean {
        if (!canOpen(rawDigits)) return false
        val next = resolver.readingOptions(rawDigits, preferredReading)
        if (next.isEmpty()) return false
        optionCode = rawDigits
        options = next
        expanded = true
        return true
    }

    fun close() {
        expanded = false
    }

    fun visibleOptions(rawDigits: String): List<String> =
        options.takeIf { expanded && optionCode == rawDigits }.orEmpty()

    fun select(rawDigits: String, reading: String): Boolean {
        val normalized = T9ZhuyinResolver.normalizeCandidateReading(reading)
        if (normalized.isEmpty() || optionCode != rawDigits || normalized !in options) return false
        selectedReading = normalized
        expanded = false
        return true
    }

    fun filterPrefixes(): List<String> = selectedReading?.let(::listOf).orEmpty()

    fun reset() {
        optionCode = ""
        options = emptyList()
        expanded = false
        selectedReading = null
    }
}

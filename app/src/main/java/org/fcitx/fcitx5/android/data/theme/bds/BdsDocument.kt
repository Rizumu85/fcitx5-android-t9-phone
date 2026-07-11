/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme.bds

import java.util.Locale

internal class BdsDocument private constructor(
    private val sections: Map<String, Map<String, String>>
) {
    operator fun get(section: String, key: String): String? =
        sections[section.uppercase(Locale.ROOT)]?.get(key.uppercase(Locale.ROOT))

    fun values(key: String): Sequence<String> {
        val normalized = key.uppercase(Locale.ROOT)
        return sections.values.asSequence().mapNotNull { it[normalized] }
    }

    companion object {
        fun parse(text: String): BdsDocument {
            val sections = linkedMapOf<String, MutableMap<String, String>>()
            var current = sections.getOrPut("") { linkedMapOf() }
            text.lineSequence().forEach { sourceLine ->
                val line = sourceLine.trim().removePrefix("\uFEFF")
                if (line.startsWith('[') && line.endsWith(']')) {
                    val name = line.substring(1, line.length - 1).trim().uppercase(Locale.ROOT)
                    current = sections.getOrPut(name) { linkedMapOf() }
                } else {
                    val separator = line.indexOf('=')
                    if (separator > 0) {
                        val key = line.substring(0, separator).trim().uppercase(Locale.ROOT)
                        val value = line.substring(separator + 1).trim()
                        current[key] = value
                    }
                }
            }
            return BdsDocument(sections)
        }
    }
}

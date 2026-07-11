/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar

enum class ToolbarButtonOrder(val storageId: String) {
    Undo("undo"),
    Redo("redo"),
    VoiceInput("voice_input"),
    TextEditing("text_editing"),
    Clipboard("clipboard");

    companion object {
        val default = entries

        fun decode(value: String): List<ToolbarButtonOrder> {
            val stored = value.split(',').mapNotNull { id -> entries.find { it.storageId == id } }
            if (VoiceInput !in stored) {
                val insertion = (stored.indexOf(Redo) + 1).coerceAtLeast(0)
                return stored.toMutableList().apply { add(insertion, VoiceInput) } +
                    entries.filterNot { it in stored || it == VoiceInput }
            }
            return (stored + entries).distinct()
        }

        fun encode(order: List<ToolbarButtonOrder>) = order.joinToString(",") { it.storageId }
    }
}

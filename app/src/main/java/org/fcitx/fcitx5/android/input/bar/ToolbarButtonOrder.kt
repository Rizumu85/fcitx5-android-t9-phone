/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar

enum class ToolbarButtonOrder(val storageId: String) {
    Undo("undo"),
    Redo("redo"),
    VoiceInput("voice_input"),
    Handwriting("handwriting"),
    TextEditing("text_editing"),
    Clipboard("clipboard");

    companion object {
        val default = entries

        fun decode(value: String): List<ToolbarButtonOrder> {
            val stored = value.split(',')
                .mapNotNull { id -> entries.find { it.storageId == id } }
                .distinct()
                .toMutableList()
            if (VoiceInput !in stored) {
                stored.add((stored.indexOf(Redo) + 1).coerceAtLeast(0), VoiceInput)
            }
            if (Handwriting !in stored) {
                stored.add((stored.indexOf(VoiceInput) + 1).coerceAtLeast(0), Handwriting)
            }
            return (stored + entries).distinct()
        }

        fun encode(order: List<ToolbarButtonOrder>) = order.joinToString(",") { it.storageId }
    }
}

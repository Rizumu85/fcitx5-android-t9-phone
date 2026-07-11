/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core

internal object FirstRunInputMethodPolicy {
    const val RIME = "rime"
    const val INTERNAL_PASSWORD_KEYBOARD = "keyboard-us"

    fun initialEnabledInputMethods(available: Collection<String>): List<String>? {
        if (RIME !in available) return null
        return buildList {
            add(RIME)
            if (INTERNAL_PASSWORD_KEYBOARD in available) {
                add(INTERNAL_PASSWORD_KEYBOARD)
            }
        }
    }

    fun isUserVisible(uniqueName: String): Boolean =
        uniqueName != INTERNAL_PASSWORD_KEYBOARD

    fun preserveInternalInputMethods(
        userSelection: Collection<String>,
        available: Collection<String>
    ): List<String> = buildList {
        addAll(userSelection.filter(::isUserVisible))
        if (INTERNAL_PASSWORD_KEYBOARD in available) {
            add(INTERNAL_PASSWORD_KEYBOARD)
        }
    }.distinct()
}

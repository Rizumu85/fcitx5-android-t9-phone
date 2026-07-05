/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

enum class T9InputMode(
    val label: String
) {
    CHINESE("中"),
    ENGLISH("En"),
    NUMBER("123");

    fun next(): T9InputMode = when (this) {
        CHINESE -> ENGLISH
        ENGLISH -> NUMBER
        NUMBER -> CHINESE
    }
}

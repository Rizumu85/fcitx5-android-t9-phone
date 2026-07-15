/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.wm

class AuxiliaryInputReturnSession {
    private var target: EssentialWindow.Key? = null

    fun begin(returnTarget: EssentialWindow.Key) {
        target = returnTarget
    }

    fun hasTarget(): Boolean = target != null

    fun consume(): EssentialWindow.Key? = target.also { target = null }

    fun clear() {
        target = null
    }
}

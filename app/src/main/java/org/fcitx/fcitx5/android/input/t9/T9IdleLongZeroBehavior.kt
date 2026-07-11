/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class T9IdleLongZeroBehavior(override val stringRes: Int) : ManagedPreferenceEnum {
    LiteralZero(R.string.t9_idle_long_zero_literal),
    VoiceInput(R.string.t9_idle_long_zero_voice)
}

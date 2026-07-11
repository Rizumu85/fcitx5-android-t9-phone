/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.os.Build
import android.view.inputmethod.EditorInfo
import org.fcitx.fcitx5.android.core.CapabilityFlag
import org.fcitx.fcitx5.android.core.CapabilityFlags
import splitties.bitflags.hasFlag

internal object VoiceInputEditorPolicy {
    fun allows(info: EditorInfo, flags: CapabilityFlags): Boolean {
        val privateMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            info.imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        return !privateMode && !flags.has(CapabilityFlag.Password)
    }
}

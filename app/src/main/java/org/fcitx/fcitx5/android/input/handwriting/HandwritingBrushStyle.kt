/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class HandwritingBrushStyle(override val stringRes: Int) : ManagedPreferenceEnum {
    PEN(R.string.handwriting_brush_pen),
    CALLIGRAPHY(R.string.handwriting_brush_calligraphy)
}

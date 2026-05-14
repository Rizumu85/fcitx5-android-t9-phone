/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import android.view.View
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import splitties.dimensions.dp

internal fun View.inputPanelTopCornerRadiusPx(): Int {
    return dp(ThemeManager.prefs.inputPanelTopRadius.getValue())
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

/**
 * A single action contract keeps the visible rails and physical shortcuts behaviorally identical.
 */
enum class HandwritingAction {
    OPEN_EMOJI,
    DELETE_TEXT,
    OPEN_NUMBER,
    INSERT_SPACE,
    SWITCH_LANGUAGE,
    INSERT_COMMA,
    OPEN_SYMBOLS,
    RETURN
}

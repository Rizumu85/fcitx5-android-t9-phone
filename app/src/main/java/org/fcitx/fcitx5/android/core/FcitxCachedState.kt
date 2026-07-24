/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core

data class FcitxCachedState(
    val inputMethodEntry: InputMethodEntry,
    val statusAreaActions: Array<Action> = emptyArray(),
    val clientPreedit: FormattedText = FormattedText.Empty,
    val inputPanel: FcitxEvent.InputPanelEvent.Data = FcitxEvent.InputPanelEvent.Data(),
    val pagedCandidates: FcitxEvent.PagedCandidateEvent.Data =
        FcitxEvent.PagedCandidateEvent.Data.Empty,
    val rimeAvailability: FcitxEvent.RimeAvailabilityEvent.Data =
        FcitxEvent.RimeAvailabilityEvent.Data.Unavailable,
    val revision: Long = 0L
)

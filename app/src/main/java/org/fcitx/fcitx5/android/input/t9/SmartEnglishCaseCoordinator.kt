/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class SmartEnglishCaseCoordinator(
    private val cycleCase: () -> Unit,
    private val pendingMultiTapDisplay: () -> String?,
    private val setComposingText: (String) -> Unit,
    private val caseLabel: () -> String,
    private val showModeIndicator: (String) -> Unit
) {
    fun cycle() {
        cycleCase()
        refreshVisibleCaseState()
    }

    private fun refreshVisibleCaseState() {
        val pendingText = pendingMultiTapDisplay()
        if (pendingText != null) {
            setComposingText(pendingText)
            return
        }
        showModeIndicator(caseLabel())
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingModelRequestPolicyTest {
    @Test
    fun `missing and failed models always accept an explicit download request`() {
        assertTrue(
            HandwritingModelRequestPolicy.canDownload(HandwritingModelUiState.MISSING)
        )
        assertTrue(
            HandwritingModelRequestPolicy.canDownload(HandwritingModelUiState.FAILED)
        )
    }

    @Test
    fun `non-actionable model states ignore duplicate requests`() {
        assertFalse(
            HandwritingModelRequestPolicy.canDownload(HandwritingModelUiState.CHECKING)
        )
        assertFalse(
            HandwritingModelRequestPolicy.canDownload(HandwritingModelUiState.DOWNLOADING)
        )
        assertFalse(
            HandwritingModelRequestPolicy.canDownload(HandwritingModelUiState.AVAILABLE)
        )
    }
}

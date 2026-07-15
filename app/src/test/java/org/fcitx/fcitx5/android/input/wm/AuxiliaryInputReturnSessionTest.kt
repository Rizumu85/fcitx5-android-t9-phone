/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.wm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxiliaryInputReturnSessionTest {
    private object HandwritingTarget : EssentialWindow.Key

    @Test
    fun auxiliaryDrawersReturnToTheirSourceExactlyOnce() {
        val session = AuxiliaryInputReturnSession()

        session.begin(HandwritingTarget)

        assertTrue(session.hasTarget())
        assertSame(HandwritingTarget, session.consume())
        assertFalse(session.hasTarget())
        assertSame(null, session.consume())
    }

    @Test
    fun explicitExitClearsStaleReturnTarget() {
        val session = AuxiliaryInputReturnSession()
        session.begin(HandwritingTarget)

        session.clear()

        assertFalse(session.hasTarget())
    }
}

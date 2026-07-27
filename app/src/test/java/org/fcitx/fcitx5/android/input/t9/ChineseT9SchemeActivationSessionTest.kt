/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseT9SchemeActivationSessionTest {
    @Test
    fun schemaIdAndLocalizedNameApplyOneLogicalActivation() {
        val session = ChineseT9SchemeActivationSession(ChineseT9Scheme.ZHUYIN)
        session.observeRimeIdentity("注音九键")

        val schemaId = requireNotNull(session.observeRimeIdentity("t9"))
        val localizedName = requireNotNull(session.observeRimeIdentity("拼音九键"))

        assertTrue(schemaId.shouldApply)
        assertTrue(schemaId.forceReset)
        assertFalse(localizedName.shouldApply)
        assertFalse(localizedName.forceReset)
        assertEquals(ChineseT9Scheme.PINYIN, session.activeScheme)
        assertEquals("拼音九键", session.activeIdentity)
    }

    @Test
    fun identityClearLetsAnEngineRestartReestablishTheSameScheme() {
        val session = ChineseT9SchemeActivationSession()
        session.observeRimeIdentity("拼音九键")
        session.clearIdentity()

        val restored = requireNotNull(session.observeRimeIdentity("t9"))

        assertTrue(restored.shouldApply)
        assertFalse(restored.forceReset)
    }

    @Test
    fun blankRimeSubModeDoesNotSplitOneLogicalActivation() {
        val session = ChineseT9SchemeActivationSession()
        session.observeRimeIdentity("拼音九键")

        assertEquals(null, session.observeRimeIdentity(""))
        val restored = requireNotNull(session.observeRimeIdentity("拼音九键"))

        assertFalse(restored.shouldApply)
        assertFalse(restored.forceReset)
    }

    @Test
    fun unknownNonBlankRimeSubModeEndsTheLogicalActivation() {
        val session = ChineseT9SchemeActivationSession()
        session.observeRimeIdentity("拼音九键")

        assertEquals(null, session.observeRimeIdentity("generic-schema"))
        val restored = requireNotNull(session.observeRimeIdentity("拼音九键"))

        assertTrue(restored.shouldApply)
        assertFalse(restored.forceReset)
    }
}

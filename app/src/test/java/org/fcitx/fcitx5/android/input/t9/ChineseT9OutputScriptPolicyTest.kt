/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseT9OutputScriptPolicyTest {
    @Test
    fun phoneticSchemasUseTraditionalizationPolarity() {
        listOf(ChineseT9Scheme.PINYIN, ChineseT9Scheme.ZHUYIN).forEach { scheme ->
            assertEquals(
                ChineseT9RimeOption("traditionalization", false),
                ChineseT9OutputScriptPolicy.optionFor(
                    scheme,
                    ChineseT9OutputScript.Simplified
                )
            )
            assertEquals(
                ChineseT9RimeOption("traditionalization", true),
                ChineseT9OutputScriptPolicy.optionFor(
                    scheme,
                    ChineseT9OutputScript.Traditional
                )
            )
        }
    }

    @Test
    fun strokeUsesInverseSimplificationOption() {
        assertEquals(
            ChineseT9RimeOption("simplification", true),
            ChineseT9OutputScriptPolicy.optionFor(
                ChineseT9Scheme.STROKE,
                ChineseT9OutputScript.Simplified
            )
        )
        assertEquals(
            ChineseT9RimeOption("simplification", false),
            ChineseT9OutputScriptPolicy.optionFor(
                ChineseT9Scheme.STROKE,
                ChineseT9OutputScript.Traditional
            )
        )
    }

    @Test
    fun newerRequestInvalidatesAnOlderAssignment() {
        val session = ChineseT9OutputScriptSession()
        val first = requireNotNull(
            session.enterScheme(ChineseT9Scheme.PINYIN, ChineseT9OutputScript.Simplified)
        )
        val second = requireNotNull(
            session.reapplyActiveScheme(
                ChineseT9Scheme.PINYIN,
                ChineseT9OutputScript.Traditional
            )
        )

        assertEquals(false, session.isCurrent(first, ChineseT9Scheme.PINYIN))
        assertEquals(true, session.isCurrent(second, ChineseT9Scheme.PINYIN))
    }

    @Test
    fun schemeTransitionOrInvalidationRejectsAssignment() {
        val session = ChineseT9OutputScriptSession()
        val schemeRequest = requireNotNull(
            session.enterScheme(ChineseT9Scheme.PINYIN, ChineseT9OutputScript.Simplified)
        )
        assertEquals(false, session.isCurrent(schemeRequest, ChineseT9Scheme.STROKE))

        session.invalidate()
        assertEquals(false, session.isCurrent(schemeRequest, ChineseT9Scheme.PINYIN))
    }

    @Test
    fun duplicateActivationDoesNotOverwriteAManualToggle() {
        val session = ChineseT9OutputScriptSession()
        requireNotNull(
            session.enterScheme(ChineseT9Scheme.PINYIN, ChineseT9OutputScript.Simplified)
        )

        assertEquals(
            null,
            session.enterScheme(ChineseT9Scheme.PINYIN, ChineseT9OutputScript.Simplified)
        )

        session.leaveRime()
        val nextVisit = session.enterScheme(
            ChineseT9Scheme.PINYIN,
            ChineseT9OutputScript.Simplified
        )
        assertEquals(true, nextVisit != null)
    }

    @Test
    fun readyRestartCanReapplyAfterDeployment() {
        val session = ChineseT9OutputScriptSession()
        val beforeDeployment = requireNotNull(
            session.enterScheme(ChineseT9Scheme.ZHUYIN, ChineseT9OutputScript.Traditional)
        )
        session.invalidate()
        val afterDeployment = session.onRimeReady(
            ChineseT9Scheme.ZHUYIN,
            ChineseT9OutputScript.Traditional
        )

        assertEquals(false, session.isCurrent(beforeDeployment, ChineseT9Scheme.ZHUYIN))
        assertEquals(true, session.isCurrent(afterDeployment, ChineseT9Scheme.ZHUYIN))
    }
}

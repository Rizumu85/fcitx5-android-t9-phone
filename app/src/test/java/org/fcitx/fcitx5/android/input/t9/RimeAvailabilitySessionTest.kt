/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RimeAvailabilitySessionTest {
    @Test
    fun deploymentAndReadyTransitionsOwnOneGenerationSequence() {
        val session = RimeAvailabilitySession()

        val deploying = session.update(data(FcitxEvent.RimeAvailabilityEvent.State.Deploying))
        val ready = session.update(
            data(FcitxEvent.RimeAvailabilityEvent.State.Ready, "t9_pinyin")
        )

        assertFalse(deploying.becameReady)
        assertTrue(ready.becameReady)
        assertEquals(2L, session.current.generation)
        assertEquals("t9_pinyin", session.current.activeSchema)
    }

    @Test
    fun emptyNativeSchemaPreservesTheObservedActiveSchema() {
        val session = RimeAvailabilitySession()
        session.observeActiveSchema("t9_zhuyin")

        session.update(data(FcitxEvent.RimeAvailabilityEvent.State.Ready))

        assertEquals("t9_zhuyin", session.current.activeSchema)
    }

    @Test
    fun deploymentClearsTheSchemaFromThePreviousEngineGeneration() {
        val session = RimeAvailabilitySession(
            data(FcitxEvent.RimeAvailabilityEvent.State.Ready, "t9_stroke")
        )

        session.update(data(FcitxEvent.RimeAvailabilityEvent.State.Deploying))
        session.update(data(FcitxEvent.RimeAvailabilityEvent.State.Ready))

        assertEquals("", session.current.activeSchema)
        assertEquals(
            RimeAvailabilitySession.EngineReadiness.SELECTING_SCHEMA,
            session.engineReadiness(
                rimeInputMethodActive = true,
                expectedScheme = ChineseT9Scheme.STROKE
            )
        )
    }

    @Test
    fun genericPinyinIsNotReadyForPhysicalT9Digits() {
        val session = RimeAvailabilitySession(
            data(FcitxEvent.RimeAvailabilityEvent.State.Ready, "雾凇拼音")
        )

        assertEquals(
            RimeAvailabilitySession.EngineReadiness.SELECTING_SCHEMA,
            session.engineReadiness(
                rimeInputMethodActive = true,
                expectedScheme = ChineseT9Scheme.PINYIN
            )
        )
    }

    @Test
    fun exactT9SchemaAndActiveRimeAreBothRequired() {
        val session = RimeAvailabilitySession(
            data(FcitxEvent.RimeAvailabilityEvent.State.Ready, "t9")
        )

        assertEquals(
            RimeAvailabilitySession.EngineReadiness.ACTIVATING_INPUT_METHOD,
            session.engineReadiness(
                rimeInputMethodActive = false,
                expectedScheme = ChineseT9Scheme.PINYIN
            )
        )
        assertEquals(
            RimeAvailabilitySession.EngineReadiness.READY,
            session.engineReadiness(
                rimeInputMethodActive = true,
                expectedScheme = ChineseT9Scheme.PINYIN
            )
        )
    }

    @Test
    fun exactImeObservationCanRecoverFromAnEarlyUnavailableCallback() {
        val session = RimeAvailabilitySession(
            data(FcitxEvent.RimeAvailabilityEvent.State.Unavailable, "t9")
        )

        assertEquals(
            RimeAvailabilitySession.EngineReadiness.SELECTING_SCHEMA,
            session.engineReadiness(
                rimeInputMethodActive = true,
                expectedScheme = ChineseT9Scheme.PINYIN
            )
        )
    }

    @Test
    fun repeatedPublicationDoesNotAdvanceGeneration() {
        val initial = data(FcitxEvent.RimeAvailabilityEvent.State.Ready, "t9_stroke")
        val session = RimeAvailabilitySession(initial)

        val transition = session.update(initial)

        assertFalse(transition.changed)
        assertEquals(0L, session.current.generation)
    }

    @Test
    fun failureLeavesReadyAndIsExplicitlyClassified() {
        val session = RimeAvailabilitySession(
            data(FcitxEvent.RimeAvailabilityEvent.State.Ready, "t9_pinyin")
        )

        val transition = session.update(data(FcitxEvent.RimeAvailabilityEvent.State.Failed))

        assertTrue(transition.leftReady)
        assertTrue(transition.failed)
    }

    private fun data(
        state: FcitxEvent.RimeAvailabilityEvent.State,
        schema: String = ""
    ) = FcitxEvent.RimeAvailabilityEvent.Data(state, schema)
}

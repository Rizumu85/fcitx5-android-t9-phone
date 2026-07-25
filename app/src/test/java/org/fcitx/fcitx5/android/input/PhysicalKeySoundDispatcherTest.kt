/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.view.KeyEvent
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeySoundDispatcherTest {
    @Test
    fun `global observer covers keys diverted before input method dispatch`() {
        assertEquals(
            InputFeedbacks.SoundEffect.Delete,
            globalEffect(KeyEvent.KEYCODE_BACK)
        )
        listOf(
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_CALL,
            KeyEvent.KEYCODE_ENDCALL
        ).forEach { keyCode ->
            assertEquals(InputFeedbacks.SoundEffect.Standard, globalEffect(keyCode))
        }
    }

    @Test
    fun `global observer leaves already covered typing and volume keys alone`() {
        assertNull(globalEffect(KeyEvent.KEYCODE_2))
        assertNull(globalEffect(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(globalEffect(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `input method keeps the existing sound mapping`() {
        assertEquals(
            InputFeedbacks.SoundEffect.Delete,
            localEffect(KeyEvent.KEYCODE_DEL)
        )
        assertEquals(
            InputFeedbacks.SoundEffect.Return,
            localEffect(KeyEvent.KEYCODE_ENTER)
        )
        assertEquals(
            InputFeedbacks.SoundEffect.SpaceBar,
            localEffect(KeyEvent.KEYCODE_DPAD_CENTER)
        )
        assertEquals(
            InputFeedbacks.SoundEffect.Standard,
            localEffect(KeyEvent.KEYCODE_4)
        )
    }

    @Test
    fun `repeat events never produce another sound`() {
        assertNull(
            PhysicalKeySoundPolicy.effect(
                source = PhysicalKeySoundDispatcher.Source.GLOBAL_OBSERVER,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                keyCode = KeyEvent.KEYCODE_BACK
            )
        )
    }

    @Test
    fun `same physical event delivered through both services is deduplicated`() {
        val filter = PhysicalKeySoundDuplicateFilter()

        assertTrue(filter.accept(KeyEvent.KEYCODE_BACK, 3, 100L))
        assertFalse(filter.accept(KeyEvent.KEYCODE_BACK, 3, 100L))
        assertTrue(filter.accept(KeyEvent.KEYCODE_BACK, 3, 101L))
    }

    private fun globalEffect(keyCode: Int) =
        PhysicalKeySoundPolicy.effect(
            source = PhysicalKeySoundDispatcher.Source.GLOBAL_OBSERVER,
            action = KeyEvent.ACTION_DOWN,
            repeatCount = 0,
            keyCode = keyCode
        )

    private fun localEffect(keyCode: Int) =
        PhysicalKeySoundPolicy.effect(
            source = PhysicalKeySoundDispatcher.Source.INPUT_METHOD,
            action = KeyEvent.ACTION_DOWN,
            repeatCount = 0,
            keyCode = keyCode
        )
}

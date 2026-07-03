/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalT9KeyPolicyTest {

    @Test
    fun mapsPhoneDigitsSeparatelyFromDecimalDigits() {
        assertEquals(0, PhysicalT9KeyPolicy.t9Digit(KeyEvent.KEYCODE_0))
        assertEquals(9, PhysicalT9KeyPolicy.t9Digit(KeyEvent.KEYCODE_9))
        assertNull(PhysicalT9KeyPolicy.t9Digit(KeyEvent.KEYCODE_NUMPAD_9))

        assertEquals(9, PhysicalT9KeyPolicy.decimalDigit(KeyEvent.KEYCODE_NUMPAD_9))
        assertNull(PhysicalT9KeyPolicy.decimalDigit(KeyEvent.KEYCODE_A))
    }

    @Test
    fun mapsCandidateShortcutLabels() {
        assertEquals(0, PhysicalT9KeyPolicy.candidateShortcutIndex(KeyEvent.KEYCODE_1))
        assertEquals(8, PhysicalT9KeyPolicy.candidateShortcutIndex(KeyEvent.KEYCODE_9))
        assertEquals(9, PhysicalT9KeyPolicy.candidateShortcutIndex(KeyEvent.KEYCODE_0))
        assertNull(PhysicalT9KeyPolicy.candidateShortcutIndex(KeyEvent.KEYCODE_NUMPAD_1))
    }

    @Test
    fun classifiesDeleteAndConfirmVariants() {
        assertTrue(PhysicalT9KeyPolicy.isDeleteKey(KeyEvent.KEYCODE_DEL))
        assertTrue(PhysicalT9KeyPolicy.isDeleteKey(KeyEvent.KEYCODE_BACK))
        assertTrue(PhysicalT9KeyPolicy.isDeleteKey(KeyEvent.KEYCODE_FORWARD_DEL))
        assertFalse(PhysicalT9KeyPolicy.isDeleteKey(KeyEvent.KEYCODE_DPAD_LEFT))

        assertEquals(
            PhysicalT9KeyPolicy.FocusKey.OK,
            PhysicalT9KeyPolicy.focusKey(KeyEvent.KEYCODE_DPAD_CENTER)
        )
        assertEquals(
            PhysicalT9KeyPolicy.FocusKey.OK,
            PhysicalT9KeyPolicy.focusKey(KeyEvent.KEYCODE_BUTTON_SELECT)
        )
        assertEquals(
            PhysicalT9KeyPolicy.FocusKey.OK,
            PhysicalT9KeyPolicy.focusKey(KeyEvent.KEYCODE_ENTER)
        )
        assertEquals(
            PhysicalT9KeyPolicy.FocusKey.OK,
            PhysicalT9KeyPolicy.focusKey(KeyEvent.KEYCODE_NUMPAD_ENTER)
        )

        assertEquals(
            PhysicalT9KeyPolicy.ConfirmAction.SELECT,
            PhysicalT9KeyPolicy.confirmAction(KeyEvent.KEYCODE_BUTTON_SELECT)
        )
        assertEquals(
            PhysicalT9KeyPolicy.ConfirmAction.RETURN,
            PhysicalT9KeyPolicy.confirmAction(KeyEvent.KEYCODE_NUMPAD_ENTER)
        )
        assertEquals(
            KeyEvent.KEYCODE_SPACE,
            PhysicalT9KeyPolicy.mappedInputModeKey(KeyEvent.KEYCODE_BUTTON_SELECT)
        )
        assertEquals(
            KeyEvent.KEYCODE_ENTER,
            PhysicalT9KeyPolicy.mappedInputModeKey(KeyEvent.KEYCODE_NUMPAD_ENTER)
        )
        assertEquals(
            KeyEvent.KEYCODE_DEL,
            PhysicalT9KeyPolicy.mappedInputModeKey(KeyEvent.KEYCODE_BACK)
        )
    }

    @Test
    fun classifiesPendingPunctuationControls() {
        assertTrue(PhysicalT9KeyPolicy.isPendingPunctuationControlKey(KeyEvent.KEYCODE_1))
        assertTrue(PhysicalT9KeyPolicy.isPendingPunctuationControlKey(KeyEvent.KEYCODE_STAR))
        assertTrue(PhysicalT9KeyPolicy.isPendingPunctuationControlKey(KeyEvent.KEYCODE_POUND))
        assertTrue(PhysicalT9KeyPolicy.isPendingPunctuationControlKey(KeyEvent.KEYCODE_FORWARD_DEL))
        assertTrue(PhysicalT9KeyPolicy.isPendingPunctuationControlKey(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(PhysicalT9KeyPolicy.isPendingPunctuationControlKey(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertFalse(PhysicalT9KeyPolicy.isPendingPunctuationControlKey(KeyEvent.KEYCODE_A))
    }
}

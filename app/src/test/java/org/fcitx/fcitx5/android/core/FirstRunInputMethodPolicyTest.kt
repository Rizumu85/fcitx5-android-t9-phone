/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FirstRunInputMethodPolicyTest {

    @Test
    fun waitsForRimeBeforeCompletingFirstRunSetup() {
        assertNull(
            FirstRunInputMethodPolicy.initialEnabledInputMethods(
                listOf("keyboard-us", "pinyin")
            )
        )
    }

    @Test
    fun enablesRimeFirstAndKeepsPasswordKeyboardInternal() {
        assertEquals(
            listOf("rime", "keyboard-us"),
            FirstRunInputMethodPolicy.initialEnabledInputMethods(
                listOf("keyboard-us", "pinyin", "rime")
            )
        )
        assertFalse(FirstRunInputMethodPolicy.isUserVisible("keyboard-us"))
    }

    @Test
    fun advancedChangesPreservePasswordKeyboardWithoutShowingIt() {
        assertEquals(
            listOf("rime", "pinyin", "keyboard-us"),
            FirstRunInputMethodPolicy.preserveInternalInputMethods(
                userSelection = listOf("rime", "pinyin"),
                available = listOf("rime", "pinyin", "keyboard-us")
            )
        )
    }
}

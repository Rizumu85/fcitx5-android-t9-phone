/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.status

import org.fcitx.fcitx5.android.core.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentStatusActionCoordinatorTest {
    @Test
    fun restoresAUserDisabledToggleAfterRimeReloadsItsDefault() {
        val values = mutableMapOf<String, String>()
        val coordinator = PersistentStatusActionCoordinator(values)
        val disableEmoji = action(name = "fcitx-rime-t9-emoji", label = "emoji on to off")
        val enableEmoji = action(name = "fcitx-rime-t9-emoji", label = "emoji off to on")

        assertTrue(coordinator.recordUserActivation(disableEmoji, fromSchemeMenu = false))
        assertTrue(values.isEmpty())
        assertEquals(emptyList<Action>(), coordinator.actionsNeedingRestore(arrayOf(disableEmoji)))
        assertEquals(emptyList<Action>(), coordinator.actionsNeedingRestore(arrayOf(enableEmoji)))
        assertEquals(listOf(disableEmoji), coordinator.actionsNeedingRestore(arrayOf(disableEmoji)))
        assertEquals(emptyList<Action>(), coordinator.actionsNeedingRestore(arrayOf(disableEmoji)))
    }

    @Test
    fun neverPersistsInputSchemeSelection() {
        val values = mutableMapOf<String, String>()
        val coordinator = PersistentStatusActionCoordinator(values)

        assertFalse(coordinator.recordUserActivation(action("luna", "active"), fromSchemeMenu = true))
        assertTrue(values.isEmpty())
    }

    private fun action(name: String, label: String) = Action(
        id = 7,
        isSeparator = false,
        isCheckable = true,
        isChecked = false,
        name = name,
        icon = "",
        shortText = label,
        longText = name,
        menu = null
    )
}

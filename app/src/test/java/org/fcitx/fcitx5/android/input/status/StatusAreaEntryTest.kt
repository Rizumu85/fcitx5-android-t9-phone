/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.status

import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Companion.isRimeAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusAreaEntryTest {

    @Test
    fun rimeActionIsDetectedByNameOrIcon() {
        assertTrue(action(name = "fcitx-rime").isRimeAction())
        assertTrue(action(icon = "fcitx_rime_dark").isRimeAction())
        assertTrue(action(menu = arrayOf(action(name = "fcitx-rime-deploy"))).isRimeAction())
    }

    @Test
    fun genericDeployMenuDoesNotBecomeRime() {
        val nonRime = action(
            name = "generic-config",
            shortText = "Generic",
            menu = arrayOf(
                action(shortText = "Deploy"),
                action(shortText = "Synchronize"),
                action(shortText = "重新部署"),
                action(shortText = "同步")
            )
        )

        assertFalse(nonRime.isRimeAction())
    }

    private fun action(
        name: String = "",
        icon: String = "",
        shortText: String = "",
        longText: String = "",
        menu: Array<Action>? = null
    ): Action =
        Action(
            id = 0,
            isSeparator = false,
            isCheckable = false,
            isChecked = false,
            name = name,
            icon = icon,
            shortText = shortText,
            longText = longText,
            menu = menu
        )
}

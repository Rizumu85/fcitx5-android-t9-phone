/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.status

import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Companion.activeMenuLabelForAction
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Companion.isRimeSchemeSwitchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusAreaEntryTest {

    @Test
    fun rimeSchemeSwitchIsDetectedBySourceActionName() {
        val schemeSwitch = action(
            name = "fcitx-rime-im",
            shortText = "雾凇拼音",
            menu = arrayOf(
                action(shortText = "Latin Mode"),
                action(shortText = "朙月拼音"),
                action(shortText = "雾凇拼音"),
                action(isSeparator = true),
                action(name = "fcitx-rime-deploy", shortText = "Deploy"),
                action(name = "fcitx-rime-sync", shortText = "Synchronize")
            )
        )

        assertTrue(schemeSwitch.isRimeSchemeSwitchAction())
    }

    @Test
    fun rimeSchemeSwitchProvidesActiveMenuLabel() {
        val schemeSwitch = action(name = "fcitx-rime-im", shortText = "雾凇拼音")

        assertEquals("雾凇拼音", activeMenuLabelForAction(schemeSwitch))
    }

    @Test
    fun nonSchemeRimeActionsDoNotProvideActiveMenuLabel() {
        val rimeOptionMenu = action(
            name = "fcitx-rime-luna_pinyin-select-ascii_punct",
            shortText = "Current"
        )

        assertNull(activeMenuLabelForAction(rimeOptionMenu))
    }

    @Test
    fun rimeDeployAndSyncActionsAreNotSchemeSwitches() {
        val rimeConfig = action(
            name = "fcitx-rime-config",
            shortText = "Rime",
            menu = arrayOf(
                action(shortText = "Deploy"),
                action(shortText = "Synchronize"),
                action(shortText = "重新部署"),
                action(shortText = "同步")
            )
        )

        assertFalse(rimeConfig.isRimeSchemeSwitchAction())
        assertFalse(action(name = "fcitx-rime-deploy", shortText = "Deploy").isRimeSchemeSwitchAction())
        assertFalse(action(name = "fcitx-rime-sync", shortText = "Synchronize").isRimeSchemeSwitchAction())
    }

    @Test
    fun otherRimeFeatureMenusKeepTheirOwnLabels() {
        val featureMenu = action(
            name = "fcitx-rime-schema-select-ascii_mode",
            shortText = "Rime Options",
            menu = arrayOf(
                action(shortText = "ASCII Mode", isCheckable = true, isChecked = true),
                action(shortText = "Full Shape", isCheckable = true)
            )
        )

        assertFalse(featureMenu.isRimeSchemeSwitchAction())
    }

    @Test
    fun rimeOptionMenuThatLooksLikeCurrentValueKeepsItsLabel() {
        val rimeOptionMenu = action(
            name = "fcitx-rime-luna_pinyin-select-ascii_punct",
            shortText = "Current",
            menu = arrayOf(
                action(shortText = "Current", isCheckable = true, isChecked = true),
                action(shortText = "Other", isCheckable = true)
            )
        )

        assertFalse(rimeOptionMenu.isRimeSchemeSwitchAction())
    }

    private fun action(
        name: String = "",
        icon: String = "",
        shortText: String = "",
        longText: String = "",
        isSeparator: Boolean = false,
        isCheckable: Boolean = false,
        isChecked: Boolean = false,
        menu: Array<Action>? = null
    ): Action =
        Action(
            id = 0,
            isSeparator = isSeparator,
            isCheckable = isCheckable,
            isChecked = isChecked,
            name = name,
            icon = icon,
            shortText = shortText,
            longText = longText,
            menu = menu
        )
}

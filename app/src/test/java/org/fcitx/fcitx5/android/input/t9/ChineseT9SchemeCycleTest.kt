/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseT9SchemeCycleTest {
    @Test
    fun followsStableSchemeOrderAndWraps() {
        val enabled = listOf(
            ChineseT9Scheme.PINYIN,
            ChineseT9Scheme.STROKE,
            ChineseT9Scheme.ZHUYIN
        )

        assertEquals(
            ChineseT9Scheme.STROKE,
            ChineseT9SchemeCycle.next(ChineseT9Scheme.PINYIN, enabled)
        )
        assertEquals(
            ChineseT9Scheme.PINYIN,
            ChineseT9SchemeCycle.next(ChineseT9Scheme.ZHUYIN, enabled)
        )
    }

    @Test
    fun excludedCurrentSchemeMovesToFirstEnabledScheme() {
        assertEquals(
            ChineseT9Scheme.PINYIN,
            ChineseT9SchemeCycle.next(
                current = ChineseT9Scheme.STROKE,
                enabled = listOf(ChineseT9Scheme.PINYIN)
            )
        )
    }

    @Test
    fun oneEnabledCurrentSchemeLeavesIdlePoundForReturn() {
        assertEquals(
            null,
            ChineseT9SchemeCycle.next(
                current = ChineseT9Scheme.PINYIN,
                enabled = listOf(ChineseT9Scheme.PINYIN)
            )
        )
    }

    @Test
    fun resolvesRenamedAndLegacyPinyinSchemaActions() {
        val renamed = action(id = 11, shortText = "拼音九键")
        val legacy = action(id = 12, shortText = "中文九键")

        assertEquals(
            11,
            ChineseT9SchemeCycle.findAction(
                arrayOf(schemeMenu(renamed)),
                ChineseT9Scheme.PINYIN
            )?.id
        )
        assertEquals(
            12,
            ChineseT9SchemeCycle.findAction(
                arrayOf(schemeMenu(legacy)),
                ChineseT9Scheme.PINYIN
            )?.id
        )
    }

    @Test
    fun rapidRequestsAdvanceFromThePendingTarget() {
        val session = ChineseT9SchemeCycleSession()
        val enabled = ChineseT9Scheme.entries

        assertEquals(
            ChineseT9Scheme.STROKE,
            session.requestNext(ChineseT9Scheme.PINYIN, enabled)
        )
        assertEquals(
            ChineseT9Scheme.ZHUYIN,
            session.requestNext(ChineseT9Scheme.PINYIN, enabled)
        )
        assertEquals(
            ChineseT9Scheme.PINYIN,
            session.requestNext(ChineseT9Scheme.PINYIN, enabled)
        )
    }

    private fun schemeMenu(vararg actions: Action): Action = action(
        id = 1,
        name = "fcitx-rime-im",
        menu = arrayOf(*actions)
    )

    private fun action(
        id: Int,
        name: String = "",
        shortText: String = "",
        menu: Array<Action>? = null
    ): Action = Action(
        id = id,
        isSeparator = false,
        isCheckable = false,
        isChecked = false,
        name = name,
        icon = "",
        shortText = shortText,
        longText = "",
        menu = menu
    )
}

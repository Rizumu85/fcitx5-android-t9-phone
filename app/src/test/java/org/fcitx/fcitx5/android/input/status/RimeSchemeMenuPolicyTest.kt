/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RimeSchemeMenuPolicyTest {

    @Test
    fun hidesRimeLatinModeAndBundledDesktopPinyinSchema() {
        assertNull(label("Latin Mode"))
        assertNull(label("英文模式"))
        assertNull(label("雾凇拼音"))
    }

    @Test
    fun presentsT9SchemasAsUserFacingChineseMethods() {
        assertEquals("拼音", label("拼音九键"))
        assertEquals("笔画", label("笔画九键"))
        assertEquals("注音", label("注音九键"))
    }

    @Test
    fun preservesUnknownSchemaForAdvancedDeployments() {
        assertEquals("自定义方案", label("自定义方案"))
    }

    private fun label(source: String): String? = RimeSchemeMenuPolicy.displayLabel(
        sourceLabel = source,
        pinyinLabel = "拼音",
        strokeLabel = "笔画",
        zhuyinLabel = "注音"
    )
}

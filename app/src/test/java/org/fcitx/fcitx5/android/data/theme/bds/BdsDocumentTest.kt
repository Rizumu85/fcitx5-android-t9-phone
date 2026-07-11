/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme.bds

import org.junit.Assert.assertEquals
import org.junit.Test

class BdsDocumentTest {

    @Test
    fun parsesCaseInsensitiveSectionsAndKeepsValuePunctuation() {
        val document = BdsDocument.parse(
            """
            Name=Sample Skin
            [Panel]
            back_style=222
            [LIST]
            VALUES=， 。 ？ ！
            """.trimIndent()
        )

        assertEquals("Sample Skin", document["", "name"])
        assertEquals("222", document["PANEL", "BACK_STYLE"])
        assertEquals("， 。 ？ ！", document["list", "values"])
    }
}

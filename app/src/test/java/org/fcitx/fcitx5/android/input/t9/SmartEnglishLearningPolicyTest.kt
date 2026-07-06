/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEnglishLearningPolicyTest {

    @Test
    fun learnsInNormalTextFields() {
        assertTrue(
            SmartEnglishLearningPolicy.shouldLearnWords(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
                imeOptions = 0
            )
        )
    }

    @Test
    fun respectsNoPersonalizedLearningFlag() {
        assertFalse(
            SmartEnglishLearningPolicy.shouldLearnWords(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            )
        )
    }

    @Test
    fun doesNotLearnPasswordFields() {
        val passwordVariations = listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )

        passwordVariations.forEach { variation ->
            assertFalse(
                SmartEnglishLearningPolicy.shouldLearnWords(
                    inputType = InputType.TYPE_CLASS_TEXT or variation,
                    imeOptions = 0
                )
            )
        }
    }

    @Test
    fun learnsPlainNumbersButNotNumberPasswords() {
        assertTrue(
            SmartEnglishLearningPolicy.shouldLearnWords(
                inputType = InputType.TYPE_CLASS_NUMBER,
                imeOptions = 0
            )
        )
        assertFalse(
            SmartEnglishLearningPolicy.shouldLearnWords(
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                imeOptions = 0
            )
        )
    }

    @Test
    fun doesNotLearnFromNonTextOrNumberFields() {
        assertFalse(
            SmartEnglishLearningPolicy.shouldLearnWords(
                inputType = InputType.TYPE_CLASS_PHONE,
                imeOptions = 0
            )
        )
    }
}

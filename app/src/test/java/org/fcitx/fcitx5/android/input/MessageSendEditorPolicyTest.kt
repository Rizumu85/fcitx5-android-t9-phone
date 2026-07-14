/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSendEditorPolicyTest {
    @Test
    fun `supported multiline chat field is forced to send`() {
        assertTrue(
            MessageSendEditorPolicy.shouldForceSend(
                snapshot(
                    packageName = "com.tencent.mobileqq",
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                )
            )
        )
    }

    @Test
    fun `message hint supports single line custom editor`() {
        assertTrue(
            MessageSendEditorPolicy.shouldForceSend(
                snapshot(packageName = "com.xingin.xhs", hintText = "说点什么")
            )
        )
    }

    @Test
    fun `search action is never replaced`() {
        assertFalse(
            MessageSendEditorPolicy.shouldForceSend(
                snapshot(
                    packageName = "com.tencent.mobileqq",
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                    imeOptions = EditorInfo.IME_ACTION_SEARCH
                )
            )
        )
    }

    @Test
    fun `unsupported app and password fields are rejected`() {
        assertFalse(
            MessageSendEditorPolicy.shouldForceSend(
                snapshot(
                    packageName = "com.example.notes",
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                )
            )
        )
        assertFalse(
            MessageSendEditorPolicy.shouldForceSend(
                snapshot(
                    packageName = "com.tencent.mobileqq",
                    inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    hintText = "消息"
                )
            )
        )
    }

    private fun snapshot(
        packageName: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        imeOptions: Int = EditorInfo.IME_ACTION_NONE,
        hintText: String? = null
    ) = MessageSendEditorPolicy.Snapshot(
        packageName = packageName,
        inputType = inputType,
        imeOptions = imeOptions,
        hintText = hintText
    )
}

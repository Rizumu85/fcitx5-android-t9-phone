/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.text.InputType
import android.view.inputmethod.EditorInfo

object MessageSendEditorPolicy {
    data class Snapshot(
        val packageName: String,
        val inputType: Int,
        val imeOptions: Int,
        val hintText: String?
    )

    private val exactPackages = setOf(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.tencent.wework",
        "com.ss.android.lark",
        "com.xingin.xhs",
        "com.luna.music",
        "my.maya.android"
    )

    private val packagePrefixes = listOf(
        "com.ss.android.ugc.aweme",
        "com.ss.android.article"
    )

    private val messageHints = listOf(
        "message", "reply", "comment", "chat",
        "消息", "回复", "评论", "说点什么", "留下你的想法"
    )

    fun snapshot(editorInfo: EditorInfo): Snapshot = Snapshot(
        packageName = editorInfo.packageName.orEmpty(),
        inputType = editorInfo.inputType,
        imeOptions = editorInfo.imeOptions,
        hintText = editorInfo.hintText?.toString()
    )

    fun shouldForceSend(snapshot: Snapshot): Boolean {
        if (!isSupportedPackage(snapshot.packageName)) return false
        if (!isPlainTextEditor(snapshot.inputType)) return false

        val action = snapshot.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) return false

        val isMultiline = snapshot.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val suppressesEnterAction =
            snapshot.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val hasMessageHint = snapshot.hintText.orEmpty().let { hint ->
            messageHints.any { hint.contains(it, ignoreCase = true) }
        }
        // Package name alone is not enough: the same apps also expose search and settings fields.
        return isMultiline || suppressesEnterAction || hasMessageHint
    }

    private fun isSupportedPackage(packageName: String): Boolean =
        packageName in exactPackages || packagePrefixes.any(packageName::startsWith)

    private fun isPlainTextEditor(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> false
            else -> true
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.annotation.Keep
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import splitties.views.imageResource

/**
 * T9 Physical Keyboard Layout
 * A minimal keyboard layout for T9 physical keyboard users.
 * Only shows essential function keys: Symbol, Emoji, Language, Space, Backspace, Return
 */
@SuppressLint("ViewConstructor")
class T9Keyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "T9"

        // 符号保持原样 0.15；表情 0.09、退格 0.11，固定和 0.6 → 空格 40%、回车 0.15 不变。
        // [符号 0.15, 表情 0.09, 地球 0.1, 空格 0f, 退格 0.11, 回车 0.15]
        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                LayoutSwitchKey("符号", PickerWindow.Key.Symbol.name, 0.15f, KeyDef.Appearance.Variant.Alternative),
                ImagePickerSwitchKey(
                    R.drawable.ic_baseline_tag_faces_24,
                    PickerWindow.Key.Emoji,
                    0.08f,
                    KeyDef.Appearance.Variant.Alternative
                ),
                LanguageKey(0.12f),
                SpaceKey(),
                BackspaceKey(0.10f),
                ReturnKey()
            )
        )
    }

    val lang: ImageKeyView by lazy { findViewById(R.id.button_lang) }
    val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    private val showLangSwitchKey = AppPrefs.getInstance().keyboard.showLangSwitchKey

    @Keep
    private val showLangSwitchKeyListener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        updateLangSwitchKey(v)
    }

    init {
        updateLangSwitchKey(showLangSwitchKey.getValue())
        showLangSwitchKey.registerOnChangeListener(showLangSwitchKeyListener)
    }

    private fun updateLangSwitchKey(visible: Boolean) {
        lang.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        space.mainText.text = buildString {
            append(ime.displayName)
            ime.subMode.run { label.ifEmpty { name.ifEmpty { null } } }?.let { append(" ($it)") }
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow

@SuppressLint("ViewConstructor")
class TemporaryFullKeyboard(
    context: Context,
    theme: Theme
) : TextKeyboard(context, theme, Layout, respectKeepLettersUppercase = false) {

    fun setPeekMode(enabled: Boolean) {
        showOnlyLastRow(enabled)
    }

    override fun onDetach() {
        setPeekMode(false)
        super.onDetach()
    }

    companion object {
        const val Name = "TemporaryFull"
        const val ExitTarget = "TemporaryFullExit"
        const val RowCount = 4
        private const val PasswordMainTextSize = 20f
        private const val PasswordAltTextSize = 9f

        private fun PasswordAlphabetKey(
            character: String,
            punctuation: String,
            overridePopupKeyboard: Boolean = false
        ) = AlphabetKey(
            character = character.lowercase(),
            punctuation = punctuation,
            textSize = PasswordMainTextSize,
            altTextSize = PasswordAltTextSize,
            margin = false,
            popup = if (overridePopupKeyboard) {
                arrayOf(
                    KeyDef.Popup.AltPreview(
                        character,
                        punctuation,
                        textSize = PasswordMainTextSize
                    ),
                    KeyDef.Popup.Keyboard(
                        character.lowercase(),
                        arrayOf(punctuation, character.uppercase())
                    )
                )
            } else {
                null
            }
        )

        private class ExitToT9Key : KeyDef(
            Appearance.Text(
                displayText = "T9",
                textSize = 16f,
                horizontalBias = 0.6f,
                textStyle = android.graphics.Typeface.BOLD,
                percentWidth = 0.08f,
                variant = Appearance.Variant.Alternative,
                pressHighlight = false
            ),
            setOf(
                Behavior.Press(KeyAction.LayoutSwitchAction(ExitTarget))
            ),
            arrayOf(
                Popup.Preview("T9", textSize = 16f)
            )
        )

        private class PeekKey : KeyDef(
            Appearance.Image(
                src = R.drawable.ic_baseline_visibility_24,
                percentWidth = 0.10f,
                variant = Appearance.Variant.Alternative,
                pressHighlight = false
            ),
            setOf(
                Behavior.Hold(
                    KeyAction.PasswordPeekAction(true),
                    KeyAction.PasswordPeekAction(false)
                )
            ),
            arrayOf(
                Popup.Preview("", icon = R.drawable.ic_baseline_visibility_24)
            )
        )

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                PasswordAlphabetKey("Q", "_", overridePopupKeyboard = true),
                PasswordAlphabetKey("W", ".", overridePopupKeyboard = true),
                PasswordAlphabetKey("E", ",", overridePopupKeyboard = true),
                PasswordAlphabetKey("R", ";", overridePopupKeyboard = true),
                PasswordAlphabetKey("T", "&", overridePopupKeyboard = true),
                PasswordAlphabetKey("Y", "$", overridePopupKeyboard = true),
                PasswordAlphabetKey("U", "%", overridePopupKeyboard = true),
                PasswordAlphabetKey("I", "[", overridePopupKeyboard = true),
                PasswordAlphabetKey("O", "]", overridePopupKeyboard = true),
                PasswordAlphabetKey("P", "|", overridePopupKeyboard = true)
            ),
            listOf(
                PasswordAlphabetKey("A", "@"),
                PasswordAlphabetKey("S", "*"),
                PasswordAlphabetKey("D", "+"),
                PasswordAlphabetKey("F", "-"),
                PasswordAlphabetKey("G", "="),
                PasswordAlphabetKey("H", "/"),
                PasswordAlphabetKey("J", "#"),
                PasswordAlphabetKey("K", "("),
                PasswordAlphabetKey("L", ")")
            ),
            listOf(
                CapsKey(),
                PasswordAlphabetKey("Z", "'"),
                PasswordAlphabetKey("X", ":"),
                PasswordAlphabetKey("C", "\""),
                PasswordAlphabetKey("V", "?"),
                PasswordAlphabetKey("B", "!"),
                PasswordAlphabetKey("N", "~"),
                PasswordAlphabetKey("M", "\\"),
                BackspaceKey()
            ),
            listOf(
                LayoutSwitchKey(
                    "符号",
                    PickerWindow.Key.Symbol.name,
                    0.15f,
                    KeyDef.Appearance.Variant.Alternative
                ),
                ExitToT9Key(),
                LanguageKey(0.12f),
                SpaceKey(0.0f),
                PeekKey(),
                SimpleReturnKey()
            )
        )
    }

    private class SimpleReturnKey : KeyDef(
        Appearance.Image(
            src = R.drawable.ic_baseline_keyboard_return_24,
            percentWidth = 0.15f,
            variant = Appearance.Variant.Accent,
            border = Appearance.Border.Special,
            viewId = R.id.button_return,
            pressHighlight = true
        ),
        setOf(
            Behavior.Press(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_Return)))
        )
    )
}

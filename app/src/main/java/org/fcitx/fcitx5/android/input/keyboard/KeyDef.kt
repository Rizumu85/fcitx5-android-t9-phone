/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.Typeface
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.data.InputFeedbacks

open class KeyDef(
    val appearance: Appearance,
    val behaviors: Set<Behavior>,
    val popup: Array<Popup>? = null
) {
    sealed class Appearance(
        val percentWidth: Float,
        val variant: Variant,
        val border: Border,
        val margin: Boolean,
        val viewId: Int,
        val soundEffect: InputFeedbacks.SoundEffect,
        val pressHighlight: Boolean
    ) {
        enum class Variant {
            Normal, AltForeground, Alternative, Accent
        }

        enum class Border {
            Default, On, Off, Special
        }

        open class Text(
            val displayText: String,
            val textSize: Float,
            val verticalBias: Float = 0.5f,
            val horizontalBias: Float = 0.5f,
            /**
             * `Int` constants in [Typeface].
             * Can be `NORMAL`(default), `BOLD`, `ITALIC` or `BOLD_ITALIC`
             */
            val textStyle: Int = Typeface.NORMAL,
            percentWidth: Float = 0.1f,
            variant: Variant = Variant.Normal,
            border: Border = Border.Default,
            margin: Boolean = true,
            viewId: Int = -1,
            soundEffect: InputFeedbacks.SoundEffect = InputFeedbacks.SoundEffect.Standard,
            pressHighlight: Boolean = false
        ) : Appearance(percentWidth, variant, border, margin, viewId, soundEffect, pressHighlight)

        class AltText(
            displayText: String,
            val altText: String,
            textSize: Float,
            val altTextSize: Float = 10.666667f,
            /**
             * `Int` constants in [Typeface].
             * Can be `NORMAL`(default), `BOLD`, `ITALIC` or `BOLD_ITALIC`
             */
            textStyle: Int = Typeface.NORMAL,
            percentWidth: Float = 0.1f,
            variant: Variant = Variant.Normal,
            border: Border = Border.Default,
            margin: Boolean = true,
            viewId: Int = -1,
            pressHighlight: Boolean = false
        ) : Text(
            displayText = displayText,
            textSize = textSize,
            textStyle = textStyle,
            percentWidth = percentWidth,
            variant = variant,
            border = border,
            margin = margin,
            viewId = viewId,
            pressHighlight = pressHighlight
        )

        class Image(
            @DrawableRes
            val src: Int,
            percentWidth: Float = 0.1f,
            variant: Variant = Variant.Normal,
            border: Border = Border.Default,
            margin: Boolean = true,
            viewId: Int = -1,
            soundEffect: InputFeedbacks.SoundEffect = InputFeedbacks.SoundEffect.Standard,
            pressHighlight: Boolean = false
        ) : Appearance(percentWidth, variant, border, margin, viewId, soundEffect, pressHighlight)

        class ImageText(
            displayText: String,
            textSize: Float,
            /**
             * `Int` constants in [Typeface].
             * Can be `NORMAL`(default), `BOLD`, `ITALIC` or `BOLD_ITALIC`
             */
            textStyle: Int = Typeface.NORMAL,
            @DrawableRes
            val src: Int,
            percentWidth: Float = 0.1f,
            variant: Variant = Variant.Normal,
            border: Border = Border.Default,
            margin: Boolean = true,
            viewId: Int = -1,
            pressHighlight: Boolean = false
        ) : Text(
            displayText = displayText,
            textSize = textSize,
            textStyle = textStyle,
            percentWidth = percentWidth,
            variant = variant,
            border = border,
            margin = margin,
            viewId = viewId,
            pressHighlight = pressHighlight
        )
    }

    sealed class Behavior {
        class Press(
            val action: KeyAction
        ) : Behavior()

        class LongPress(
            val action: KeyAction
        ) : Behavior()

        class Repeat(
            val action: KeyAction
        ) : Behavior()

        class Swipe(
            val action: KeyAction
        ) : Behavior()

        class DoubleTap(
            val action: KeyAction
        ) : Behavior()

        class Hold(
            val downAction: KeyAction,
            val upAction: KeyAction
        ) : Behavior()
    }

    sealed class Popup {
        open class Preview(
            val content: String,
            @DrawableRes val icon: Int? = null,
            val textSize: Float? = null
        ) : Popup()

        class AltPreview(
            content: String,
            val alternative: String,
            textSize: Float? = null
        ) : Preview(content, textSize = textSize)

        class Keyboard(val label: String, val keys: Array<String>? = null) : Popup()

        class Menu(val items: Array<Item>) : Popup() {
            class Item(val label: String, @DrawableRes val icon: Int, val action: KeyAction)
        }
    }
}

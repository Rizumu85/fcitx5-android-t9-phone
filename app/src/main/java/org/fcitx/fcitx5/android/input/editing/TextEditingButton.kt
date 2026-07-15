/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.editing

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.InputUiFont
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.input.keyboard.insetRadiusDrawable
import org.fcitx.fcitx5.android.input.keyboard.shadowedKeyBackgroundDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageResource

@SuppressLint("ViewConstructor")
class TextEditingButton(
    ctx: Context,
    private val theme: Theme,
    private val bordered: Boolean,
    private val radius: Float,
    private val altStyle: Boolean = false
) : CustomGestureView(ctx) {

    // bordered
    private val shadowWidth = dp(1)
    private val hInset = dp(4)
    private val vInset = dp(4)

    private val backgroundColor =
        if (altStyle) theme.altKeyBackgroundColor else theme.keyBackgroundColor

    init {
        background = normalBackground(backgroundColor)
        foreground = pressedForeground()
    }

    val textView = textView {
        isClickable = false
        isFocusable = false
        background = null
        InputUiFont.applyTo(this)
        setTextColor(if (altStyle) theme.altKeyTextColor else theme.keyTextColor)
    }

    val imageView = imageView {
        isClickable = false
        isFocusable = false
        imageTintList = ColorStateList.valueOf(
            if (altStyle) theme.altKeyTextColor else theme.keyTextColor
        )
    }

    fun setText(id: Int) {
        textView.setText(id)
        removeView(imageView)
        add(textView, lParams(wrapContent, wrapContent, gravityCenter))
    }

    fun setIcon(@DrawableRes icon: Int) {
        imageView.imageResource = icon
        removeView(textView)
        add(imageView, lParams(wrapContent, wrapContent, gravityCenter))
    }

    fun enableActivatedState() {
        textView.setTextColor(
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_activated),
                    intArrayOf(android.R.attr.state_enabled)
                ),
                intArrayOf(
                    theme.genericActiveForegroundColor,
                    if (altStyle) theme.altKeyTextColor else theme.keyTextColor
                )
            )
        )
        imageView.imageTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_activated),
                intArrayOf(android.R.attr.state_enabled)
            ),
            intArrayOf(
                theme.genericActiveForegroundColor,
                theme.altKeyTextColor
            )
        )
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_activated),
                normalBackground(theme.genericActiveBackgroundColor)
            )
            addState(intArrayOf(), normalBackground(backgroundColor))
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else DisabledAlpha
    }

    private fun normalBackground(color: Int) = if (bordered) {
        shadowedKeyBackgroundDrawable(
            color,
            theme.keyShadowColor,
            radius,
            shadowWidth,
            hInset,
            vInset
        )
    } else {
        // Keep the entire grid cell as the hit target while the inset surface provides the same
        // independent-key rhythm used by handwriting and other direct-touch input controls.
        insetRadiusDrawable(hInset, vInset, radius, color)
    }

    private fun pressedForeground() = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            insetRadiusDrawable(hInset, vInset, radius, theme.keyPressHighlightColor)
        )
    }

    private companion object {
        const val DisabledAlpha = 0.4f
    }
}

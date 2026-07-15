/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.editing

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
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
    private val shape: Shape = Shape.Standalone
) : CustomGestureView(ctx) {

    enum class Shape {
        Standalone,
        DpadUp,
        DpadRight,
        DpadDown,
        DpadLeft,
        DpadCenter
    }

    // bordered
    private val shadowWidth = dp(1)
    private val hInset = dp(4)
    private val vInset = dp(4)

    private val backgroundColor = theme.keyBackgroundColor
    private var visuallyPressed = false

    init {
        background = when (shape) {
            Shape.Standalone -> normalBackground(backgroundColor)
            Shape.DpadCenter -> dpadSegmentBackground(theme.altKeyBackgroundColor)
            else -> ColorDrawable(Color.TRANSPARENT)
        }
        foreground = pressedForeground()
    }

    val textView = textView {
        isClickable = false
        isFocusable = false
        background = null
        InputUiFont.applyTo(this)
        setTextColor(theme.keyTextColor)
    }

    val imageView = imageView {
        isClickable = false
        isFocusable = false
        imageTintList = ColorStateList.valueOf(theme.keyTextColor)
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
                    theme.keyTextColor
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
                    theme.keyTextColor
                )
        )
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_activated),
                shapedBackground(theme.genericActiveBackgroundColor)
            )
            addState(
                intArrayOf(),
                if (shape == Shape.DpadCenter) {
                    dpadSegmentBackground(theme.altKeyBackgroundColor)
                } else {
                    shapedBackground(backgroundColor)
                }
            )
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        // Keep disabled actions in the same solid key grid so selection changes do not make the
        // editing surface look as though a key disappeared; only its content recedes.
        textView.alpha = if (enabled) 1f else DisabledContentAlpha
        imageView.alpha = if (enabled) 1f else DisabledContentAlpha
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        if (visuallyPressed != isPressed) {
            visuallyPressed = isPressed
            animatePress(isPressed)
        }
        return handled
    }

    private fun animatePress(pressed: Boolean) {
        // A short depth change makes the controls feel mechanically connected without moving the
        // layout or delaying repeat events, which remain owned by CustomGestureView.
        animate().cancel()
        animate()
            .scaleX(if (pressed) PressedScale else 1f)
            .scaleY(if (pressed) PressedScale else 1f)
            .translationY(if (pressed) dp(PressedDepthDp).toFloat() else 0f)
            .setDuration(if (pressed) PressDurationMs else ReleaseDurationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
        translationY = 0f
        visuallyPressed = false
        super.onDetachedFromWindow()
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

    private fun shapedBackground(color: Int) = when (shape) {
        Shape.Standalone -> normalBackground(color)
        else -> dpadSegmentBackground(color)
    }

    private fun dpadSegmentBackground(color: Int) = when (shape) {
        Shape.DpadCenter -> insetRadiusDrawable(hInset, vInset, radius, color)
        else -> GradientDrawable().apply {
            setColor(color)
            cornerRadii = when (this@TextEditingButton.shape) {
                Shape.DpadUp -> floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
                Shape.DpadRight -> floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
                Shape.DpadDown -> floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
                Shape.DpadLeft -> floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
                else -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            }
        }
    }

    private fun pressedForeground() = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            shapedBackground(theme.keyPressHighlightColor)
        )
    }

    private companion object {
        const val DisabledContentAlpha = 0.4f
        const val PressedScale = 0.97f
        const val PressedDepthDp = 1
        const val PressDurationMs = 55L
        const val ReleaseDurationMs = 95L
    }
}

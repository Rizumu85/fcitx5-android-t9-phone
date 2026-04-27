/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.core.add
import splitties.views.dsl.core.view

private class SelectionActionGuideView(
    context: Context,
    guideColor: Int
) : View(context) {

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = guideColor
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(5f)), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val padding = dp(14f)
        canvas.drawLine(padding, cy, width - padding, cy, paint)
        canvas.drawLine(cx, padding, cx, height - padding, paint)
        canvas.drawCircle(cx, cy, (minOf(width, height) / 2f) - padding, paint)
    }
}

class SelectionActionPanel(
    private val context: Context,
    private val theme: Theme
) {

    private fun selectionActionHint(
        label: String,
        circular: Boolean = false,
        typefaceStyle: Int = Typeface.BOLD
    ) = context.view(::AutoScaleTextView) {
        alpha = 0f
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        gravity = Gravity.CENTER
        minimumWidth = if (circular) context.dp(64) else context.dp(52)
        minimumHeight = if (circular) context.dp(64) else context.dp(26)
        setPadding(if (circular) 0 else context.dp(8), 0, if (circular) 0 else context.dp(8), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, if (circular) 24f else 18f)
        InputUiFont.applyTo(this, typefaceStyle)
        text = label
        setTextColor(theme.accentKeyTextColor)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = if (circular) context.dp(32).toFloat() else context.dp(3).toFloat()
            setColor(theme.accentKeyBackgroundColor)
        }
        elevation = context.dp(8).toFloat()
    }

    private fun selectionActionVerticalHint(first: String, second: String) = context.view(::LinearLayout) {
        alpha = 0f
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumWidth = context.dp(30)
        minimumHeight = context.dp(58)
        setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(3).toFloat()
            setColor(theme.accentKeyBackgroundColor)
        }
        elevation = context.dp(8).toFloat()
        listOf(first, second).forEach { label ->
            addView(
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
                    setTextColor(theme.accentKeyTextColor)
                    InputUiFont.applyTo(this, Typeface.BOLD)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private val anchor = context.view(::View) {
        isClickable = false
        isFocusable = false
        visibility = View.INVISIBLE
    }
    private val guide = SelectionActionGuideView(
        context,
        (theme.accentKeyBackgroundColor and 0x00ffffff) or 0x66000000
    ).apply {
        alpha = 0f
        visibility = View.GONE
        isClickable = false
        isFocusable = false
    }
    private val hintUp = selectionActionHint("复制")
    private val hintLeft = selectionActionVerticalHint("剪", "切")
    private val hintCenter = selectionActionHint(
        "确认",
        circular = true,
        typefaceStyle = Typeface.NORMAL
    )
    private val hintRight = selectionActionVerticalHint("粘", "贴")
    private val hintDown = selectionActionHint("删除")

    private val hints
        get() = listOf(guide, hintUp, hintLeft, hintCenter, hintRight, hintDown)

    fun addTo(parent: ConstraintLayout) {
        parent.add(anchor, parent.lParams(context.dp(1), context.dp(1)) {
            centerVertically()
            centerHorizontally()
        })
        parent.add(guide, parent.lParams(context.dp(150), context.dp(150)) {
            centerVertically()
            centerHorizontally()
        })
        parent.add(hintUp, parent.lParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {
            above(anchor)
            centerHorizontally()
            bottomMargin = context.dp(46)
        })
        parent.add(hintLeft, parent.lParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {
            endToStartOf(hintCenter)
            centerVertically()
            marginEnd = context.dp(14)
        })
        parent.add(hintCenter, parent.lParams(context.dp(64), context.dp(64)) {
            centerVertically()
            centerHorizontally()
        })
        parent.add(hintRight, parent.lParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {
            startToEndOf(hintCenter)
            centerVertically()
            marginStart = context.dp(14)
        })
        parent.add(hintDown, parent.lParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {
            below(anchor)
            centerHorizontally()
            topMargin = context.dp(46)
        })
    }

    fun show() {
        hints.forEach { hint ->
            hint.animate().cancel()
            hint.visibility = View.VISIBLE
            hint.alpha = 0f
            hint.scaleX = 0.85f
            hint.scaleY = 0.85f
            hint.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(80L)
                .start()
        }
    }

    fun hide() {
        hints.forEach { hint ->
            hint.animate().cancel()
            hint.animate()
                .alpha(0f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(120L)
                .withEndAction {
                    hint.visibility = View.GONE
                }
                .start()
        }
    }
}

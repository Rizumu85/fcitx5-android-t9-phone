/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.FrameLayout
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp

internal class TransientModeIndicatorOverlay(
    context: Context,
    theme: Theme
) {
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable(::hide)
    private val root = AutoScaleTextView(context).apply {
        alpha = 0f
        visibility = GONE
        isClickable = false
        isFocusable = false
        gravity = Gravity.CENTER
        minimumWidth = context.dp(52)
        minimumHeight = context.dp(26)
        setPadding(context.dp(8), 0, context.dp(8), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
        InputUiFont.applyTo(this, Typeface.BOLD)
        setTextColor(theme.accentKeyTextColor)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(3f)
            setColor(theme.accentKeyBackgroundColor)
        }
        elevation = context.dp(8).toFloat()
    }

    fun attachTo(parent: FrameLayout) {
        detach()
        // Product decision: this must be the final IME-root child. CandidateView is a later
        // sibling of InputView, so an indicator inside InputView can never draw above it.
        parent.addView(
            root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    fun bringToFront() {
        root.bringToFront()
    }

    fun show(label: String) {
        handler.removeCallbacks(hideRunnable)
        root.animate().cancel()
        root.text = label
        root.visibility = VISIBLE
        root.alpha = 0f
        root.scaleX = 0.85f
        root.scaleY = 0.85f
        root.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(80L)
            .withEndAction {
                handler.postDelayed(hideRunnable, 420L)
            }
            .start()
    }

    fun detach() {
        handler.removeCallbacks(hideRunnable)
        root.animate().cancel()
        (root.parent as? ViewGroup)?.removeView(root)
    }

    private fun hide() {
        root.animate().cancel()
        root.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(120L)
            .withEndAction {
                root.visibility = GONE
            }
            .start()
    }
}

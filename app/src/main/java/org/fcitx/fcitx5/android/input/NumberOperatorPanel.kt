/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.content.Context
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
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add

class NumberOperatorPanel(
    private val context: Context,
    private val theme: Theme
) {

    private fun hintCell(primary: String, secondary: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumWidth = context.dp(50)
        minimumHeight = context.dp(42)
        setPadding(context.dp(6), context.dp(4), context.dp(6), context.dp(4))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(3).toFloat()
            setColor(theme.accentKeyBackgroundColor)
        }
        addView(
            TextView(context).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                text = primary
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
                setTextColor(theme.accentKeyTextColor)
                InputUiFont.applyTo(this, Typeface.NORMAL)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        addView(
            TextView(context).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                text = secondary
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19f)
                setTextColor(theme.accentKeyTextColor)
                InputUiFont.applyTo(this, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun hintRow(vararg cells: Pair<String, String>) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        cells.forEachIndexed { index, cell ->
            addView(
                hintCell(cell.first, cell.second),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) marginStart = context.dp(8)
                }
            )
        }
    }

    private val operatorPanel = LinearLayout(context).apply {
        alpha = 0f
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        listOf(
            arrayOf("1" to "-", "2" to "+", "3" to "="),
            arrayOf("4" to "π", "5" to "/", "6" to "≈"),
            arrayOf("7" to "(", "8" to "%", "9" to ")"),
            arrayOf("*" to "*", "0" to ".", "#" to "返回")
        ).forEachIndexed { index, row ->
            addView(
                hintRow(*row),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = context.dp(8)
                }
            )
        }
    }

    private val equalsChoicePanel = LinearLayout(context).apply {
        alpha = 0f
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun addTo(parent: ConstraintLayout) {
        parent.add(operatorPanel, parent.lParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {
            centerVertically()
            centerHorizontally()
        })
        parent.add(equalsChoicePanel, parent.lParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {
            centerVertically()
            centerHorizontally()
        })
    }

    fun showOperators() {
        showTransientPanel(operatorPanel)
    }

    fun hideOperators() {
        hideTransientPanel(operatorPanel)
    }

    fun showEqualsChoice(prefix: String, result: String) {
        equalsChoicePanel.removeAllViews()
        equalsChoicePanel.addView(
            hintCell("确认", "$prefix$result").apply {
                minimumWidth = context.dp(76)
                minimumHeight = context.dp(48)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        showTransientPanel(equalsChoicePanel)
    }

    fun hideEqualsChoice() {
        hideTransientPanel(equalsChoicePanel)
    }

    private fun showTransientPanel(panel: View) {
        panel.animate().cancel()
        panel.visibility = View.VISIBLE
        panel.alpha = 0f
        panel.scaleX = 0.9f
        panel.scaleY = 0.9f
        panel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(80L)
            .start()
    }

    private fun hideTransientPanel(panel: View) {
        panel.animate().cancel()
        panel.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(120L)
            .withEndAction {
                panel.visibility = View.GONE
            }
            .start()
    }
}

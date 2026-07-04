/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.InputUiFont
import org.fcitx.fcitx5.android.utils.alpha
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.core.Ui

class StatusActionMenuUi(
    override val ctx: Context,
    private val theme: Theme,
    private val actions: Array<Action>,
    private val activeMenuLabel: String?,
    private val onActionClick: (Action) -> Unit
) : Ui {

    private val itemRadius = ctx.dp(8).toFloat()

    private val list = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(10), ctx.dp(6), ctx.dp(10), ctx.dp(6))
    }

    private var activeRow: View? = null

    override val root = ScrollView(ctx).apply {
        backgroundColor = theme.barColor
        isFillViewport = true
        clipToPadding = false
        addView(
            list,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    init {
        visibleMenuItems().forEach { item ->
            when (item) {
                MenuItem.Separator -> {
                    list.addView(separatorView())
                }
                is MenuItem.ActionRow -> {
                    val row = actionRow(item.action, item.label, item.activeStyle)
                    if (item.activeStyle != ActiveStyle.None) {
                        activeRow = row
                    }
                    list.addView(
                        row,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ctx.dp(46)
                        ).apply {
                            topMargin = ctx.dp(2)
                            bottomMargin = ctx.dp(2)
                        }
                    )
                }
            }
        }
        activeRow?.let { row ->
            root.post {
                root.scrollTo(0, (row.top - ctx.dp(6)).coerceAtLeast(0))
            }
        }
    }

    private fun visibleMenuItems(): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        var pendingSeparator = false
        actions.forEach { action ->
            if (action.isSeparator) {
                if (items.lastOrNull() is MenuItem.ActionRow) {
                    pendingSeparator = true
                }
                return@forEach
            }
            val label = StatusAreaEntry.labelForActionMenuItem(action) ?: return@forEach
            if (pendingSeparator && items.lastOrNull() is MenuItem.ActionRow) {
                items.add(MenuItem.Separator)
            }
            pendingSeparator = false
            items.add(MenuItem.ActionRow(action, label, activeStyleForMenuItem(action, label)))
        }
        return items
    }

    private sealed class MenuItem {
        data class ActionRow(
            val action: Action,
            val label: String,
            val activeStyle: ActiveStyle
        ) : MenuItem()
        object Separator : MenuItem()
    }

    private enum class ActiveStyle {
        None,
        Checked,
        CurrentScheme
    }

    private fun activeStyleForMenuItem(action: Action, label: String): ActiveStyle =
        when {
            action.isChecked -> ActiveStyle.Checked
            activeMenuLabel?.let { it == label } == true -> ActiveStyle.CurrentScheme
            else -> ActiveStyle.None
        }

    private fun actionRow(action: Action, label: String, activeStyle: ActiveStyle) = TextView(ctx).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setSingleLine(false)
        setTextColor(
            if (activeStyle != ActiveStyle.None) {
                theme.genericActiveForegroundColor
            } else {
                theme.keyTextColor
            }
        )
        setPadding(ctx.dp(16), 0, ctx.dp(16), 0)
        InputUiFont.applyTo(this)
        if (activeStyle == ActiveStyle.CurrentScheme) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, activeIndicatorDrawable(), null)
            compoundDrawablePadding = ctx.dp(12)
        }
        background = rowBackground(activeStyle == ActiveStyle.Checked)
        setOnClickListener { onActionClick(action) }
    }

    private fun activeIndicatorDrawable() =
        ContextCompat.getDrawable(ctx, R.drawable.ic_baseline_check_24)?.let { drawable ->
            DrawableCompat.wrap(drawable.mutate()).also {
                DrawableCompat.setTint(it, theme.genericActiveForegroundColor)
            }
        }

    private fun separatorView() = FrameLayout(ctx).apply {
        addView(
            View(ctx).apply {
                backgroundColor = theme.keyTextColor.alpha(0.24f)
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ctx.dp(1),
                Gravity.CENTER
            ).apply {
                leftMargin = ctx.dp(16)
                rightMargin = ctx.dp(16)
            }
        )
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ctx.dp(12)
        )
    }

    private fun rowBackground(active: Boolean) = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            roundedDrawable(theme.keyPressHighlightColor)
        )
        addState(
            intArrayOf(),
            if (active) {
                roundedDrawable(theme.genericActiveBackgroundColor)
            } else {
                ColorDrawable(Color.TRANSPARENT)
            }
        )
    }

    private fun roundedDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = itemRadius
        setColor(color)
    }
}

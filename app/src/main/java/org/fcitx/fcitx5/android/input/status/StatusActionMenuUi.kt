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
    private val onActionClick: (Action) -> Unit
) : Ui {

    private val itemRadius = ctx.dp(8).toFloat()

    private val list = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(10), ctx.dp(6), ctx.dp(10), ctx.dp(6))
    }

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
                    list.addView(
                        actionRow(item.action, item.label),
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
            items.add(MenuItem.ActionRow(action, label))
        }
        return items
    }

    private sealed class MenuItem {
        data class ActionRow(val action: Action, val label: String) : MenuItem()
        object Separator : MenuItem()
    }

    private fun actionRow(action: Action, label: String) = TextView(ctx).apply {
        text = if (action.isChecked) {
            ctx.getString(R.string.status_action_current_item, label)
        } else {
            label
        }
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setSingleLine(false)
        setTextColor(if (action.isChecked) theme.genericActiveForegroundColor else theme.keyTextColor)
        setPadding(ctx.dp(16), 0, ctx.dp(16), 0)
        InputUiFont.applyTo(this)
        background = rowBackground(action.isChecked)
        setOnClickListener { onActionClick(action) }
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

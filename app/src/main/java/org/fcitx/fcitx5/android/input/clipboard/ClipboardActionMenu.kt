/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.clipboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.InputUiFont
import org.fcitx.fcitx5.android.utils.borderlessRippleDrawable
import splitties.dimensions.dp
import kotlin.math.max

internal class ClipboardActionMenu(
    private val anchor: View,
    private val theme: Theme,
    actions: List<Action>,
    private val onDismiss: (ClipboardActionMenu) -> Unit
) {
    data class Action(
        @StringRes val label: Int,
        @DrawableRes val icon: Int,
        val invoke: () -> Unit
    )

    private val context: Context = anchor.context
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, context.dp(MenuVerticalPaddingDp), 0, context.dp(MenuVerticalPaddingDp))
        background = GradientDrawable().apply {
            cornerRadius = context.dp(MenuRadiusDp)
            setColor(theme.popupBackgroundColor)
        }
        actions.forEach { action -> addView(actionRow(action)) }
    }
    private val popup: PopupWindow = PopupWindow(
        content,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    ).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isOutsideTouchable = true
        isClippingEnabled = true
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        elevation = context.dp(MenuElevationDp).toFloat()
        setOnDismissListener {
            anchor.isActivated = false
            onDismiss(this@ClipboardActionMenu)
        }
    }

    fun show() {
        val displayWidth = context.resources.displayMetrics.widthPixels
        val displayHeight = context.resources.displayMetrics.heightPixels
        val availableWidth = (displayWidth - context.dp(MenuScreenInsetDp * 2))
            .coerceAtLeast(context.dp(MenuMinimumWidthDp))
        val menuWidth = max(anchor.width, context.dp(MenuMinimumWidthDp)).coerceAtMost(availableWidth)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val menuHeight = content.measuredHeight
        val anchorLocation = IntArray(2).also(anchor::getLocationOnScreen)
        val windowFrame = Rect().also(anchor::getWindowVisibleDisplayFrame)
        val screenInset = context.dp(MenuScreenInsetDp)
        val anchorSpacing = context.dp(MenuAnchorSpacingDp)
        val x = anchorLocation[0].coerceIn(
            screenInset,
            (displayWidth - menuWidth - screenInset).coerceAtLeast(screenInset)
        )
        val spaceBelow = displayHeight - (anchorLocation[1] + anchor.height)
        val preferredY = if (spaceBelow >= menuHeight + anchorSpacing + screenInset) {
            anchorLocation[1] + anchor.height + anchorSpacing
        } else {
            anchorLocation[1] - menuHeight - anchorSpacing
        }
        val y = preferredY.coerceIn(
            screenInset,
            (displayHeight - menuHeight - screenInset).coerceAtLeast(screenInset)
        )

        popup.width = menuWidth
        popup.height = menuHeight
        anchor.isActivated = true
        // PopupWindow does not reliably flip above anchors inside an IME window, so choose the
        // visible side explicitly after measuring the themed menu.
        popup.showAtLocation(
            anchor.rootView,
            Gravity.TOP or Gravity.START,
            x - windowFrame.left,
            y - windowFrame.top
        )
    }

    fun dismiss() {
        popup.dismiss()
    }

    private fun actionRow(action: Action) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(MenuHorizontalPaddingDp), 0, context.dp(MenuHorizontalPaddingDp), 0)
        background = borderlessRippleDrawable(
            theme.keyPressHighlightColor,
            context.dp(MenuItemRadiusDp)
        )
        contentDescription = context.getString(action.label)
        addView(
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                imageTintList = ColorStateList.valueOf(theme.altKeyTextColor)
                setImageDrawable(ContextCompat.getDrawable(context, action.icon))
            },
            LinearLayout.LayoutParams(context.dp(MenuIconSizeDp), context.dp(MenuIconSizeDp))
        )
        addView(
            TextView(context).apply {
                setText(action.label)
                setTextColor(theme.popupTextColor)
                textSize = MenuTextSizeSp
                gravity = Gravity.CENTER_VERTICAL
                InputUiFont.applyTo(this)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = context.dp(MenuIconTextSpacingDp)
            }
        )
        setOnClickListener {
            popup.dismiss()
            action.invoke()
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            context.dp(MenuItemHeightDp)
        )
    }

    private companion object {
        const val MenuMinimumWidthDp = 164
        const val MenuScreenInsetDp = 8
        const val MenuRadiusDp = 12f
        const val MenuItemRadiusDp = 8
        const val MenuElevationDp = 6
        const val MenuVerticalPaddingDp = 4
        const val MenuHorizontalPaddingDp = 14
        const val MenuItemHeightDp = 44
        const val MenuIconSizeDp = 22
        const val MenuIconTextSpacingDp = 14
        const val MenuAnchorSpacingDp = 2
        const val MenuTextSizeSp = 14f
    }
}

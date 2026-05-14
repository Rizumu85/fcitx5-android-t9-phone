/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.InputUiFont
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.gravityCenter
import splitties.views.imageResource

class PopupEntryUi(override val ctx: Context, theme: Theme, keyHeight: Int, radius: Float) : Ui {

    private val defaultTextSize = 18f

    var lastShowTime = -1L

    val textView = view(::TextView) {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, defaultTextSize)
        includeFontPadding = false
        isSingleLine = true
        InputUiFont.applyTo(this)
        gravity = gravityCenter
        setTextColor(theme.popupTextColor)
    }

    val iconView = imageView {
        visibility = View.GONE
        imageTintList = ColorStateList.valueOf(theme.popupTextColor)
        scaleType = ImageView.ScaleType.CENTER
    }

    override val root = constraintLayout {
        background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(theme.popupBackgroundColor)
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = dp(2f)
        add(textView, lParams(matchParent, keyHeight) {
            topOfParent()
            centerHorizontally()
        })
        add(iconView, lParams(matchParent, keyHeight) {
            topOfParent()
            centerHorizontally()
        })
    }

    fun setContent(text: String, @DrawableRes icon: Int? = null, textSize: Float? = null) {
        if (icon == null) {
            iconView.visibility = View.GONE
            textView.visibility = View.VISIBLE
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize ?: defaultTextSize)
            textView.text = text
        } else {
            textView.visibility = View.GONE
            iconView.visibility = View.VISIBLE
            iconView.imageResource = icon
        }
    }
}

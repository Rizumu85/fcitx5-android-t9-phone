/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.icu.text.BreakIterator
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.InputUiFont
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter

class StatusAreaEntryUi(override val ctx: Context, private val theme: Theme) : Ui {

    private val defaultLabelSizeSp = 11f
    private val longLabelSizeSp = 10f

    private val activeLabelBackground = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = ctx.dp(7).toFloat()
    }

    val label = textView {
        textSize = defaultLabelSizeSp
        InputUiFont.applyTo(this)
        gravity = gravityCenter
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        includeFontPadding = false
        setLineSpacing(0f, 0.86f)
        maxLines = 6
        setTextColor(theme.keyTextColor)
    }

    override val root = object : CustomGestureView(ctx) {
        init {
            clipChildren = false
            clipToPadding = false
            addView(
                label,
                FrameLayout.LayoutParams(
                    wrapContent,
                    wrapContent,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                ).apply {
                    topMargin = ctx.dp(4)
                }
            )
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    fun setEntry(entry: StatusAreaEntry) {
        val compactLabel = normalizeVerticalLabel(entry.label)
        val labelCodePoints = compactLabel.codePointCount(0, compactLabel.length)
        label.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            if (labelCodePoints > 5) longLabelSizeSp else defaultLabelSizeSp
        )
        label.setTextColor(if (entry.active) theme.genericActiveForegroundColor else theme.keyTextColor)
        label.background = if (entry.active) {
            activeLabelBackground.apply {
                setColor(theme.genericActiveBackgroundColor)
            }
        } else {
            null
        }
        label.setPadding(ctx.dp(5), ctx.dp(5), ctx.dp(5), ctx.dp(5))
        label.text = toVerticalLabel(compactLabel)
    }

    private fun normalizeVerticalLabel(s: String) =
        s.filterNot { it.isWhitespace() }.replace('→', '↓')

    private fun toVerticalLabel(s: String) = graphemeClusters(s).joinToString("\n")

    private fun graphemeClusters(s: String): List<String> {
        if (s.isEmpty()) return emptyList()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(s)
            buildList {
                var start = iterator.first()
                var end = iterator.next()
                while (end != BreakIterator.DONE) {
                    add(s.substring(start, end))
                    start = end
                    end = iterator.next()
                }
            }
        } else {
            buildList {
                var offset = 0
                while (offset < s.length) {
                    val next = s.offsetByCodePoints(offset, 1)
                    add(s.substring(offset, next))
                    offset = next
                }
            }
        }
    }
}

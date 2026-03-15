/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * T9 pinyin selection bar: shows candidate pinyin for current T9 segment; tap to select.
 */
package org.fcitx.fcitx5.android.input.t9

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.dependency.UniqueViewComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme

class PinyinSelectionBarComponent :
    UniqueViewComponent<PinyinSelectionBarComponent, HorizontalScrollView>(),
    InputBroadcastReceiver {

    private val context by manager.context()
    private val theme by manager.theme()
    private val service by manager.inputMethodService()

    private val container by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    override val view: HorizontalScrollView by lazy {
        HorizontalScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(container, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
            visibility = View.GONE
        }
    }

    private fun dp(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density).toInt()
    }

    override fun onInputPanelUpdate(data: org.fcitx.fcitx5.android.core.FcitxEvent.InputPanelEvent.Data) {
        val useT9 = org.fcitx.fcitx5.android.data.prefs.AppPrefs.getInstance().keyboard.useT9KeyboardLayout.getValue()
        if (!useT9) {
            view.visibility = View.GONE
            return
        }
        val candidates = service.getT9PinyinCandidates()
        if (candidates.isEmpty()) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        container.removeAllViews()
        val paddingH = dp(8)
        val paddingV = dp(6)
        val marginEndPx = dp(6)
        val radius = dp(4).toFloat()
        val bgColor = theme.keyBackgroundColor
        val textColor = theme.keyTextColor
        for (pinyin in candidates) {
            val chip = TextView(context).apply {
                text = pinyin
                setTextColor(textColor)
                textSize = 14f
                setPadding(paddingH, paddingV, paddingH, paddingV)
                background = GradientDrawable().apply {
                    setColor(bgColor)
                    cornerRadius = radius
                }
                setOnClickListener { service.selectT9Pinyin(pinyin) }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = marginEndPx
            }
            container.addView(chip, params)
        }
    }
}

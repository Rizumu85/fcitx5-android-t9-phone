/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs.NavbarBackground
import org.fcitx.fcitx5.android.input.keyboard.T9Keyboard
import org.fcitx.fcitx5.android.input.keyboard.TemporaryFullKeyboard
import org.fcitx.fcitx5.android.input.bar.ui.idle.NumberRow
import org.fcitx.fcitx5.android.utils.navbarFrameHeight
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalMargin
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view
import splitties.views.imageDrawable

class KeyboardPreviewUi(override val ctx: Context, val theme: Theme) : Ui {

    var intrinsicWidth: Int = -1
        private set

    var intrinsicHeight: Int = -1
        private set

    private val keyboardSidePaddingPx = 0
    private val keyboardBottomPaddingPx = 0

    private val navbarBackground = ThemeManager.prefs.navbarBackground
    private val keyBorder by ThemeManager.prefs.keyBorder

    private val previewPreferenceListener: ManagedPreferenceProvider.OnChangeListener =
        ManagedPreferenceProvider.OnChangeListener {
        root.post {
            setTheme(ThemeManager.activeTheme)
            recalculateSize()
        }
        }

    private val t9Background = imageView {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    private val passwordBackground = imageView {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private val barHeight = ctx.dp(40)
    private val fakeKawaiiBar = view(::View)

    private var keyboardWidth = -1
    private var keyboardHeight = -1
    private var t9KeyboardHeight = -1
    private var passwordKeyboardHeight = -1
    private lateinit var fakeT9Keyboard: T9Keyboard
    private lateinit var fakePasswordKeyboard: TemporaryFullKeyboard
    private lateinit var fakePasswordNumberRow: NumberRow

    private val t9Preview = constraintLayout {
        add(t9Background, lParams {
            centerInParent()
        })
        add(fakeKawaiiBar, lParams(height = dp(40)) {
            centerHorizontally()
        })
    }

    private val passwordPreview = constraintLayout {
        add(passwordBackground, lParams {
            centerInParent()
        })
    }

    private val previewPager = ViewPager2(ctx).apply {
        adapter = object : RecyclerView.Adapter<PreviewHolder>() {
            private val pages = listOf(t9Preview, passwordPreview)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                PreviewHolder(FrameLayout(parent.context))

            override fun getItemCount() = pages.size

            override fun onBindViewHolder(holder: PreviewHolder, position: Int) {
                val page = pages[position]
                (page.parent as? ViewGroup)?.removeView(page)
                holder.container.removeAllViews()
                holder.container.addView(page, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }
        }
    }

    private class PreviewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    override val root = object : FrameLayout(ctx) {
        init {
            add(previewPager, lParams())
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            recalculateSize()
            onSizeMeasured?.invoke(intrinsicWidth, intrinsicHeight)
            ThemeManager.prefs.registerOnChangeListener(previewPreferenceListener)
        }

        override fun onConfigurationChanged(newConfig: Configuration?) {
            recalculateSize()
        }

        override fun onDetachedFromWindow() {
            ThemeManager.prefs.unregisterOnChangeListener(previewPreferenceListener)
            super.onDetachedFromWindow()
        }
    }

    var onSizeMeasured: ((Int, Int) -> Unit)? = null

    private fun keyboardWindowSize(): Pair<Int, Int> {
        val resources = ctx.resources
        val displayMetrics = resources.displayMetrics
        val w = displayMetrics.widthPixels
        val displayHeight = displayMetrics.heightPixels
        val (t9Percent, passwordPercent) = when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> 15 to 45
            else -> 10 to 40
        }
        t9KeyboardHeight = displayHeight * t9Percent / 100
        passwordKeyboardHeight = displayHeight * passwordPercent / 100
        return w to maxOf(t9KeyboardHeight, passwordKeyboardHeight)
    }

    init {
        val (w, h) = keyboardWindowSize()
        keyboardWidth = w
        keyboardHeight = h
        setTheme(theme)
    }

    fun recalculateSize() {
        val (w, h) = keyboardWindowSize()
        keyboardWidth = w
        keyboardHeight = h
        fakeT9Keyboard.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = t9KeyboardHeight
            horizontalMargin = keyboardSidePaddingPx
        }
        fakePasswordKeyboard.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = passwordKeyboardHeight
            horizontalMargin = keyboardSidePaddingPx
        }
        fakePasswordNumberRow.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = barHeight
        }
        intrinsicWidth = keyboardWidth
        // KawaiiBar height + WindowManager view height
        intrinsicHeight = barHeight + keyboardHeight
        // extra bottom padding
        intrinsicHeight += keyboardBottomPaddingPx
        // windowInsets navbar padding
        if (navbarBackground.getValue() == NavbarBackground.Full) {
            ViewCompat.getRootWindowInsets(root)?.also {
                // IME window has different navbar height when system navigation in "gesture navigation" mode
                // thus the inset from Activity root window is unreliable
                if (it.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom > 0 ||
                    // in case navigation hint was hidden ...
                    it.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom > 0
                ) {
                    intrinsicHeight += ctx.navbarFrameHeight()
                }
            }
        }
        previewPager.updateLayoutParams<FrameLayout.LayoutParams> {
            width = intrinsicWidth
            height = intrinsicHeight
        }
        t9Preview.updateLayoutParams<FrameLayout.LayoutParams> {
            width = intrinsicWidth
            height = intrinsicHeight
        }
        passwordPreview.updateLayoutParams<FrameLayout.LayoutParams> {
            width = intrinsicWidth
            height = intrinsicHeight
        }
    }

    fun setBackground(drawable: Drawable) {
        t9Background.imageDrawable = drawable
        passwordBackground.imageDrawable = drawable
    }

    fun setTheme(theme: Theme, background: Drawable? = null) {
        setBackground(background ?: theme.backgroundDrawable(keyBorder))
        if (this::fakeT9Keyboard.isInitialized) {
            fakeT9Keyboard.onDetach()
            fakePasswordNumberRow.onDetach()
            fakePasswordKeyboard.onDetach()
            t9Preview.removeView(fakeT9Keyboard)
            passwordPreview.removeView(fakePasswordNumberRow)
            passwordPreview.removeView(fakePasswordKeyboard)
        }
        fakeKawaiiBar.backgroundColor = if (keyBorder) Color.TRANSPARENT else theme.barColor
        fakeT9Keyboard = T9Keyboard(ctx, theme).also {
            it.onAttach()
        }
        fakePasswordKeyboard = TemporaryFullKeyboard(ctx, theme).also {
            it.onAttach()
        }
        fakePasswordNumberRow = NumberRow(ctx, theme).also {
            it.onAttach()
        }
        t9Preview.apply {
            add(fakeT9Keyboard, lParams(matchConstraints, t9KeyboardHeight) {
                below(fakeKawaiiBar)
                centerHorizontally(keyboardSidePaddingPx)
            })
        }
        passwordPreview.apply {
            add(fakePasswordNumberRow, lParams(matchConstraints, barHeight) {
                centerHorizontally()
            })
            add(fakePasswordKeyboard, lParams(matchConstraints, passwordKeyboardHeight) {
                below(fakePasswordNumberRow)
                centerHorizontally(keyboardSidePaddingPx)
            })
        }
    }
}

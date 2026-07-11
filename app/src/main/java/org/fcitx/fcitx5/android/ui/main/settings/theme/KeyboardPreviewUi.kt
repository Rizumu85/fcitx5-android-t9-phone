/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs.NavbarBackground
import org.fcitx.fcitx5.android.input.keyboard.T9Keyboard
import org.fcitx.fcitx5.android.input.keyboard.TemporaryFullKeyboard
import org.fcitx.fcitx5.android.input.bar.ui.idle.NumberRow
import org.fcitx.fcitx5.android.input.clipboard.ClipboardEntryUi
import org.fcitx.fcitx5.android.input.editing.TextEditingUi
import org.fcitx.fcitx5.android.input.TopRoundedClipLayout
import org.fcitx.fcitx5.android.utils.navbarFrameHeight
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalMargin
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
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
    private var previewTheme = theme

    private var keyboardWidth = -1
    private var keyboardHeight = -1
    private var t9KeyboardHeight = -1
    private var passwordKeyboardHeight = -1
    private lateinit var fakeT9Keyboard: T9Keyboard
    private lateinit var fakePasswordKeyboard: TemporaryFullKeyboard
    private lateinit var fakePasswordNumberRow: NumberRow

    private val t9Content = TopRoundedClipLayout(ctx).apply {
        add(t9Background, lParams {
            centerInParent()
        })
        add(fakeKawaiiBar, lParams(height = dp(40)) {
            topOfParent()
            centerHorizontally()
        })
    }

    private val t9Preview = FrameLayout(ctx).apply {
        // The compact T9 preview belongs next to the paging hint, while the shared
        // viewport keeps page switching stable.
        addView(t9Content, FrameLayout.LayoutParams(matchParent, wrapContent).apply {
            gravity = Gravity.BOTTOM
        })
    }

    private val passwordPreview = TopRoundedClipLayout(ctx).apply {
        add(passwordBackground, lParams {
            centerInParent()
        })
    }

    private val textEditingPreview = TopRoundedClipLayout(ctx)
    private val clipboardPreview = TopRoundedClipLayout(ctx)
    private val previewPages = listOf(
        t9Preview,
        passwordPreview,
        textEditingPreview,
        clipboardPreview
    )

    private val previewPager = ViewPager2(ctx).apply {
        adapter = object : RecyclerView.Adapter<PreviewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                PreviewHolder(FrameLayout(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                })

            override fun getItemCount() = previewPages.size

            override fun onBindViewHolder(holder: PreviewHolder, position: Int) {
                val page = previewPages[position]
                (page.parent as? ViewGroup)?.removeView(page)
                holder.container.removeAllViews()
                holder.container.addView(page, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }
        }
    }

    private val pageIndicator = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        // Use the existing indicator strip for spacing so neither orientation
        // changes the viewport or clips the paging hint.
        setPadding(dp(6), dp(8), dp(6), 0)
        contentDescription = "T9 and password previews"
    }

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            updatePageIndicator(position)
        }
    }

    private class PreviewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    override val root = object : FrameLayout(ctx) {
        init {
            add(previewPager, lParams())
            add(pageIndicator, FrameLayout.LayoutParams(wrapContent, dp(16)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            })
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            // Build the hint after the first layout so the initial page advertises
            // the swipe affordance before the user has interacted with the pager.
            post { updatePageIndicator(previewPager.currentItem) }
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
        previewPager.registerOnPageChangeCallback(pageCallback)
        updatePageIndicator(0)
        val (w, h) = keyboardWindowSize()
        keyboardWidth = w
        keyboardHeight = h
        setTheme(theme)
        recalculateSize()
    }

    fun recalculateSize() {
        val (w, h) = keyboardWindowSize()
        keyboardWidth = w
        keyboardHeight = h
        fakeT9Keyboard.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = t9KeyboardHeight
            horizontalMargin = keyboardSidePaddingPx
        }
        t9Content.updateLayoutParams<FrameLayout.LayoutParams> {
            height = barHeight + t9KeyboardHeight
            gravity = Gravity.BOTTOM
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
        pageIndicator.updateLayoutParams<FrameLayout.LayoutParams> {
            topMargin = intrinsicHeight
        }
        if (root.isAttachedToWindow) {
            root.requestLayout()
        }
    }

    private fun updatePageIndicator(selected: Int) {
        pageIndicator.removeAllViews()
        val descriptions = arrayOf(
            "T9 preview",
            "Password preview",
            "Text editing preview",
            "Clipboard preview"
        )
        repeat(previewPages.size) { index ->
            pageIndicator.addView(View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    val color = previewTheme.keyTextColor
                    setColor(Color.argb(
                        if (index == selected) 255 else 96,
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                    ))
                }
                contentDescription = descriptions[index]
            }, LinearLayout.LayoutParams(
                ctx.dp(if (index == selected) 8 else 6),
                ctx.dp(if (index == selected) 8 else 6)
            ).apply {
                leftMargin = ctx.dp(3)
                rightMargin = ctx.dp(3)
            })
        }
    }

    fun setBackground(drawable: Drawable) {
        t9Background.imageDrawable = drawable
        passwordBackground.imageDrawable = drawable
    }

    private fun rebuildAuxiliaryPreviews(theme: Theme) {
        val prefs = ThemeManager.prefs
        val panelRadius = ctx.dp(prefs.inputPanelTopRadius.getValue())
        listOf(t9Content, passwordPreview, textEditingPreview, clipboardPreview).forEach {
            it.topCornerRadiusPx = panelRadius
        }

        textEditingPreview.apply {
            removeAllViews()
            background = theme.backgroundDrawable(keyBorder)
            val editingUi = TextEditingUi(
                ctx,
                theme,
                keyBorder,
                ctx.dp(prefs.textEditingButtonRadius.getValue().toFloat())
            )
            addView(
                editingUi.root,
                ConstraintLayout.LayoutParams(matchParent, matchParent)
            )
        }

        clipboardPreview.apply {
            removeAllViews()
            background = theme.backgroundDrawable(keyBorder)
            val radius = ctx.dp(prefs.clipboardEntryRadius.getValue().toFloat())
            val entries = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                listOf(
                    R.string.clipboard to true,
                    android.R.string.copy to false,
                    android.R.string.paste to false
                ).forEach { (textRes, pinned) ->
                    val entryUi = ClipboardEntryUi(ctx, theme, radius).apply {
                        setEntry(ctx.getString(textRes), pinned)
                    }
                    addView(entryUi.root, LinearLayout.LayoutParams(matchParent, 0, 1f).apply {
                        setMargins(dp(12), dp(5), dp(12), dp(5))
                    })
                }
            }
            addView(entries, ConstraintLayout.LayoutParams(matchParent, matchParent))
        }
    }

    fun setTheme(theme: Theme, background: Drawable? = null) {
        previewTheme = theme
        setBackground(background ?: theme.backgroundDrawable(keyBorder))
        if (this::fakeT9Keyboard.isInitialized) {
            fakeT9Keyboard.onDetach()
            fakePasswordNumberRow.onDetach()
            fakePasswordKeyboard.onDetach()
            t9Content.removeView(fakeT9Keyboard)
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
        t9Content.apply {
            add(fakeT9Keyboard, lParams(matchConstraints, t9KeyboardHeight) {
                below(fakeKawaiiBar)
                bottomOfParent()
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
        rebuildAuxiliaryPreviews(theme)
        // Both pages keep the password preview's stable viewport height; the T9
        // controls are centered inside it so paging never shifts the surrounding UI.
        updatePageIndicator(previewPager.currentItem)
    }
}

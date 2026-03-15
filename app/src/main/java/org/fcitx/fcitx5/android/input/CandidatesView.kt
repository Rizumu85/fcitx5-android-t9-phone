/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Size
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.floating.PagedCandidatesUi
import org.fcitx.fcitx5.android.input.preedit.PreeditUi
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent
import splitties.views.padding
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class CandidatesView(
    service: FcitxInputMethodService,
    fcitx: FcitxConnection,
    theme: Theme
) : BaseInputView(service, fcitx, theme) {

    private val ctx = context.withTheme(R.style.Theme_InputViewTheme)

    private val candidatesPrefs = AppPrefs.getInstance().candidates
    private val orientation by candidatesPrefs.orientation
    private val windowMinWidth by candidatesPrefs.windowMinWidth
    private val windowPadding by candidatesPrefs.windowPadding
    private val windowRadius by candidatesPrefs.windowRadius
    private val fontSize by candidatesPrefs.fontSize
    private val itemPaddingVertical by candidatesPrefs.itemPaddingVertical
    private val itemPaddingHorizontal by candidatesPrefs.itemPaddingHorizontal

    private var inputPanel = FcitxEvent.InputPanelEvent.Data()
    private var paged = FcitxEvent.PagedCandidateEvent.Data.Empty

    /**
     * horizontal, bottom, top
     */
    private val anchorPosition = floatArrayOf(0f, 0f, 0f)
    private val parentSize = floatArrayOf(0f, 0f)

    private var shouldUpdatePosition = false

    /**
     * layout update may or may not cause [CandidatesView]'s size [onSizeChanged],
     * in either case, we should reposition it
     */
    private val layoutListener = OnGlobalLayoutListener {
        shouldUpdatePosition = true
    }

    /**
     * [CandidatesView]'s position is calculated based on it's size,
     * so we need to recalculate the position after layout,
     * and before any actual drawing to avoid flicker
     */
    private val preDrawListener = OnPreDrawListener {
        if (shouldUpdatePosition) {
            updatePosition()
        }
        true
    }

    private val touchEventReceiverWindow = TouchEventReceiverWindow(this)

    private val setupTextView: TextView.() -> Unit = {
        textSize = fontSize.toFloat()
        val v = dp(itemPaddingVertical)
        val h = dp(itemPaddingHorizontal)
        setPadding(h, v, h, v)
    }

    /** Slightly smaller font for third row (candidates). */
    private val setupTextViewCandidates: TextView.() -> Unit = {
        textSize = (fontSize * 0.9f).coerceAtLeast(1f)
        val v = dp(itemPaddingVertical)
        val h = dp(itemPaddingHorizontal)
        setPadding(h, v, h, v)
    }

    /** Same as setupTextView but with smaller font for first row; line height fills row so descenders not clipped. */
    private val setupTextViewSmallRow: TextView.() -> Unit = {
        textSize = smallRowFontSizeSp
        val v = dp(itemPaddingVertical)
        val h = dp(itemPaddingHorizontal)
        setPadding(h, v, h, v)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setLineHeight(preeditRowHeightPx)
        }
        minHeight = preeditRowHeightPx
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        includeFontPadding = false
    }

    private val preeditUi = PreeditUi(ctx, theme, setupTextViewSmallRow)

    private val candidatesUi = PagedCandidatesUi(
        ctx, theme, setupTextViewCandidates,
        onCandidateClick = { index -> fcitx.launchOnReady { it.select(index) } },
        onPrevPage = { fcitx.launchOnReady { it.offsetCandidatePage(-1) } },
        onNextPage = { fcitx.launchOnReady { it.offsetCandidatePage(1) } }
    )

    /** T9 pinyin selection bar: row above candidates, replaces number row when visible. */
    private val pinyinBarContainer = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val pinyinBarScroll = HorizontalScrollView(ctx).apply {
        isFillViewport = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(pinyinBarContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        visibility = View.GONE
    }

    private fun dpCandidates(value: Int): Int = (value * ctx.resources.displayMetrics.density).toInt()

    /** Height of one candidate row (one line), used as the "11" in 6:6:11. */
    private val oneCandidateRowHeightPx: Int
        get() {
            val dm = ctx.resources.displayMetrics
            val linePx = (fontSize * dm.scaledDensity * 1.2f).toInt()
            return linePx + 2 * dpCandidates(itemPaddingVertical)
        }

    /** First row: 7/11 so descenders (g,p,y,q) are not clipped, same as second row. */
    private val preeditRowHeightPx: Int get() = oneCandidateRowHeightPx * 7 / 11
    /** Second row: 7/11 so descenders (q,p,g,y) are not clipped. */
    private val pinyinBarRowHeightPx: Int get() = oneCandidateRowHeightPx * 7 / 11

    /** Font size for first/second row (6/11 of candidate row) so text fits. */
    private val smallRowFontSizeSp: Float get() = fontSize * 6f / 11f

    /** Vertical container for the two bubbles (first row; second+third rows). */
    private val contentWrapper = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    private fun makeBubbleBackground() = GradientDrawable().apply {
        setColor(theme.backgroundColor)
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(windowRadius).toFloat()
    }

    /** Bubble 1: first row only; width = preedit content (left-aligned with bubble 2). */
    private val bubble1Wrapper = FrameLayout(ctx).apply {
        setPadding(dp(windowPadding), dp(windowPadding), dp(windowPadding), dp(windowPadding))
        background = makeBubbleBackground()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    /** Bubble 2: second + third rows; width = max of those rows. */
    private val bubble2Wrapper = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(windowPadding), dp(windowPadding), dp(windowPadding), dp(windowPadding))
        background = makeBubbleBackground()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipChildren = false
        clipToPadding = false
    }

    override fun onStartHandleFcitxEvent() {
        val inputPanelData = fcitx.runImmediately { inputPanelCached }
        handleFcitxEvent(FcitxEvent.InputPanelEvent(inputPanelData))
    }

    override fun handleFcitxEvent(it: FcitxEvent<*>) {
        when (it) {
            is FcitxEvent.InputPanelEvent -> {
                inputPanel = it.data
                updateUi()
            }
            is FcitxEvent.PagedCandidateEvent -> {
                paged = it.data
                updateUi()
            }
            else -> {}
        }
    }

    private fun evaluateVisibility(): Boolean {
        return inputPanel.preedit.isNotEmpty() ||
                paged.candidates.isNotEmpty() ||
                inputPanel.auxUp.isNotEmpty() ||
                inputPanel.auxDown.isNotEmpty() ||
                (AppPrefs.getInstance().keyboard.useT9KeyboardLayout.getValue() && service.getT9PinyinCandidates().isNotEmpty())
    }

    /** Truncate candidate pinyin (comment) by current key count: show only as many letters per syllable as keys typed. */
    private fun truncateCommentByKeyCount(comment: String, keyCount: Int): String {
        if (keyCount <= 0) return ""
        val syllables = comment.split(Regex("[\'\\s]+")).filter { it.isNotEmpty() }
        if (syllables.isEmpty()) return comment.take(keyCount)
        var remaining = keyCount
        val parts = mutableListOf<String>()
        for (syl in syllables) {
            if (remaining <= 0) break
            val take = minOf(syl.length, remaining)
            parts.add(syl.take(take))
            remaining -= take
        }
        return parts.joinToString("'")
    }

    private fun updateUi() {
        // First row: in T9 Chinese show predicted pinyin (first candidate comment) truncated by key count, or digits→pinyin
        val useT9 = AppPrefs.getInstance().keyboard.useT9KeyboardLayout.getValue()
        val t9Preedit = when {
            useT9 && paged.candidates.isNotEmpty() -> {
                val comment = paged.candidates.first().comment
                if (comment.isNotEmpty()) {
                    val keyCount = service.getT9CompositionKeyCount()
                    val display = truncateCommentByKeyCount(comment, keyCount)
                    if (display.isNotEmpty()) FormattedText(arrayOf(display), intArrayOf(TextFormatFlag.NoFlag.flag), -1)
                    else service.getT9PreeditDisplay()
                } else service.getT9PreeditDisplay()
            }
            useT9 -> service.getT9PreeditDisplay()
            else -> null
        }
        val panelToShow = t9Preedit?.let {
            FcitxEvent.InputPanelEvent.Data(it, inputPanel.auxUp, inputPanel.auxDown)
        } ?: inputPanel
        preeditUi.update(panelToShow)
        preeditUi.root.visibility = if (preeditUi.visible) VISIBLE else GONE
        candidatesUi.update(paged, orientation)
        updatePinyinBar()
        if (evaluateVisibility()) {
            visibility = VISIBLE
        } else {
            // RecyclerView won't update its items when ancestor view is GONE
            visibility = INVISIBLE
        }
    }

    private fun updatePinyinBar() {
        val useT9 = AppPrefs.getInstance().keyboard.useT9KeyboardLayout.getValue()
        if (!useT9) {
            pinyinBarScroll.visibility = View.GONE
            return
        }
        val candidates = service.getT9PinyinCandidates()
        if (candidates.isEmpty()) {
            pinyinBarScroll.visibility = View.GONE
            return
        }
        pinyinBarScroll.visibility = View.VISIBLE
        pinyinBarContainer.removeAllViews()
        val paddingH = dpCandidates(itemPaddingHorizontal)
        val paddingV = dpCandidates(itemPaddingVertical)
        val marginEndPx = dpCandidates(itemPaddingHorizontal)
        val radius = dpCandidates(windowRadius).toFloat()
        val chipBg = Color.TRANSPARENT
        val textColor = theme.candidateTextColor
        for (pinyin in candidates) {
            val chip = TextView(ctx).apply {
                text = pinyin
                setTextColor(textColor)
                textSize = smallRowFontSizeSp
                setPadding(paddingH, paddingV, paddingH, paddingV)
                minHeight = pinyinBarRowHeightPx
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                includeFontPadding = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setLineHeight(pinyinBarRowHeightPx)
                }
                background = GradientDrawable().apply {
                    setColor(chipBg)
                    this.shape = GradientDrawable.RECTANGLE
                    cornerRadius = radius
                }
                setOnClickListener { service.selectT9Pinyin(pinyin) }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = marginEndPx }
            pinyinBarContainer.addView(chip, params)
        }
    }

    private var bottomInsets = 0

    private fun updatePosition() {
        if (visibility != VISIBLE) {
            // skip unnecessary updates
            return
        }
        val (parentWidth, parentHeight) = parentSize
        if (parentWidth <= 0 || parentHeight <= 0) {
            // panic, bail
            translationX = 0f
            translationY = 0f
            return
        }
        val (horizontal, bottom, top) = anchorPosition
        val w: Int = width
        val h: Int = height
        val selfWidth = w.toFloat()
        val selfHeight = h.toFloat()
        // Left-align when possible; if bubble would go off right, shift left so it stays fully visible
        val tX: Float = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            val rtlOffset = parentWidth - horizontal
            if (rtlOffset + selfWidth > parentWidth) selfWidth - parentWidth else -rtlOffset
        } else {
            val leftAligned = horizontal
            val rightEdge = leftAligned + selfWidth
            if (rightEdge <= parentWidth) leftAligned
            else (parentWidth - selfWidth).coerceIn(0f, leftAligned)
        }
        val bottomLimit = parentHeight - bottomInsets
        val bottomSpace = bottomLimit - bottom
        // move CandidatesView above cursor anchor, only when
        val tY: Float = if (
            bottom + selfHeight > bottomLimit   // bottom space is not enough
            && top > bottomSpace                // top space is larger than bottom
        ) top - selfHeight else bottom
        translationX = tX
        translationY = tY
        // update touchEventReceiverWindow's position after CandidatesView's
        touchEventReceiverWindow.showAt(tX.roundToInt(), tY.roundToInt(), w, h)
        shouldUpdatePosition = false
    }

    fun updateCursorAnchor(@Size(4) anchor: FloatArray, @Size(2) parent: FloatArray) {
        val (horizontal, bottom, _, top) = anchor
        val (parentWidth, parentHeight) = parent
        anchorPosition[0] = horizontal
        anchorPosition[1] = bottom
        anchorPosition[2] = top
        parentSize[0] = parentWidth
        parentSize[1] = parentHeight
        updatePosition()
    }

    init {
        // invisible by default
        visibility = INVISIBLE

        minWidth = dp(windowMinWidth)
        padding = 0
        setBackgroundColor(Color.TRANSPARENT)
        // Two bubbles: bubble1 = first row (width by pinyin), bubble2 = second + third rows

        bubble1Wrapper.addView(preeditUi.root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            preeditRowHeightPx
        ).apply { gravity = Gravity.START or Gravity.TOP })

        bubble2Wrapper.addView(pinyinBarScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            pinyinBarRowHeightPx
        ).apply { gravity = Gravity.START or Gravity.TOP })
        bubble2Wrapper.addView(candidatesUi.root, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.START or Gravity.TOP })
        (candidatesUi.root as? RecyclerView)?.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                outRect.right = dp(4)
            }
        })

        contentWrapper.addView(bubble1Wrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.START })
        contentWrapper.addView(bubble2Wrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START
            topMargin = dp(4)
        })

        add(contentWrapper, lParams(wrapContent, wrapContent) {
            topOfParent()
            startOfParent()
        })

        isFocusable = false
        layoutParams = ViewGroup.LayoutParams(wrapContent, wrapContent)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            bottomInsets = getNavBarBottomInset(insets)
        }
        return insets
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE) {
            touchEventReceiverWindow.dismiss()
        }
        super.setVisibility(visibility)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        touchEventReceiverWindow.dismiss()
        super.onDetachedFromWindow()
    }
}

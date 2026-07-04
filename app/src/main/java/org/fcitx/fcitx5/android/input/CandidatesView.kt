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
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Size
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation
import org.fcitx.fcitx5.android.input.candidates.floating.PagedCandidatesUi
import org.fcitx.fcitx5.android.input.preedit.PreeditUi
import org.fcitx.fcitx5.android.input.t9.ChineseT9CandidatePipeline
import org.fcitx.fcitx5.android.input.t9.ChineseT9CandidateLoadingState
import org.fcitx.fcitx5.android.input.t9.ChineseT9InputSnapshot
import org.fcitx.fcitx5.android.input.t9.T9BulkCandidateLoader
import org.fcitx.fcitx5.android.input.t9.T9CandidateBudget
import org.fcitx.fcitx5.android.input.t9.T9CandidateFocus
import org.fcitx.fcitx5.android.input.t9.T9CandidatePager
import org.fcitx.fcitx5.android.input.t9.T9CandidateSnapshots
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiStateBuilder
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiRenderer
import org.fcitx.fcitx5.android.input.t9.T9CandidateWidthBudget
import org.fcitx.fcitx5.android.input.t9.T9PagedCandidates
import org.fcitx.fcitx5.android.input.t9.T9PinyinChipAdapter
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowWindow
import org.fcitx.fcitx5.android.input.t9.T9PresentationState
import org.fcitx.fcitx5.android.input.t9.T9ResponsivenessTrace
import org.fcitx.fcitx5.android.input.t9.T9SmartEnglishPageCache
import org.fcitx.fcitx5.android.input.t9.T9UiRefreshScheduler
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
import kotlin.math.min
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class CandidatesView(
    service: FcitxInputMethodService,
    fcitx: FcitxConnection,
    theme: Theme
) : BaseInputView(service, fcitx, theme) {

    private val ctx = context.withTheme(R.style.Theme_InputViewTheme)

    private val prefs = AppPrefs.getInstance()
    private val candidatesPrefs = prefs.candidates
    private val orientation by candidatesPrefs.orientation
    private val windowMinWidth by candidatesPrefs.windowMinWidth
    private val windowPadding by candidatesPrefs.windowPadding
    private val windowRadius by candidatesPrefs.windowRadius
    private val fontSize by candidatesPrefs.fontSize
    private val itemPaddingVertical by candidatesPrefs.itemPaddingVertical
    private val itemPaddingHorizontal by candidatesPrefs.itemPaddingHorizontal
    private val horizontalMargin by candidatesPrefs.horizontalMargin
    private val bubbleGap by candidatesPrefs.bubbleGap
    private val candidateItemSpacing by candidatesPrefs.candidateItemSpacing
    private val t9TopBottomRowRatioPercent by candidatesPrefs.t9TopBottomRowRatioPercent
    private val t9HanziCharacterBudget by candidatesPrefs.t9HanziCharacterBudget

    @Volatile
    private var t9InputModeEnabled = prefs.keyboard.useT9KeyboardLayout.getValue()

    private val t9InputModeEnabledChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            t9InputModeEnabled = value
        }

    init {
        prefs.keyboard.useT9KeyboardLayout.registerOnChangeListener(t9InputModeEnabledChangeListener)
    }

    private var inputPanel = FcitxEvent.InputPanelEvent.Data()
    private var paged = FcitxEvent.PagedCandidateEvent.Data.Empty

    /**
     * horizontal, bottom, top
     */
    private val anchorPosition = floatArrayOf(0f, 0f, 0f)
    private val parentSize = floatArrayOf(0f, 0f)

    private var shouldUpdatePosition = false
    private var showAfterPositioned = false
    private var preferAboveCursorAnchor = false
    private val chineseT9CandidateLoadingState = ChineseT9CandidateLoadingState()

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
        if (shouldUpdatePosition || showAfterPositioned) {
            updatePosition()
        }
        true
    }

    private val touchEventReceiverWindow = TouchEventReceiverWindow(this)

    private val setupTextView: TextView.() -> Unit = {
        textSize = fontSize.toFloat()
        InputUiFont.applyTo(this)
        val v = dp(itemPaddingVertical)
        val h = dp(itemPaddingHorizontal)
        setPadding(h, v, h, v)
    }

    /** Third row (candidates) use candidate font size setting as-is. */
    private val setupTextViewCandidates: TextView.() -> Unit = {
        textSize = fontSize.toFloat().coerceAtLeast(1f)
        InputUiFont.applyTo(this)
        val v = dp(itemPaddingVertical)
        val h = dp(itemPaddingHorizontal)
        setPadding(h, v, h, v)
    }

    /** Same as setupTextView but with smaller font for compact top rows; line height fills row so descenders not clipped. */
    private val setupTextViewSmallRow: TextView.() -> Unit = {
        textSize = compactTopRowFontSizeSp
        InputUiFont.applyTo(this)
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

    /**
     * Last candidate list actually shown to the user. When T9 pinyin filtering is active this
     * differs from [paged] (the raw fcitx page) and tap indices must be translated back before
     * being sent to fcitx, otherwise taps on filtered rows would pick the wrong character.
     */
    private var t9ShownPaged: FcitxEvent.PagedCandidateEvent.Data? = null
    private var t9ShownOriginalIndices = intArrayOf()
    private var t9ShownUsesBulkSelection = false
    private var t9ShownMatchedPrefix: String? = null
    private var t9ShownUsesPendingPunctuation = false
    private val t9PendingPunctuationPager = T9CandidatePager()
    private var t9BulkFilteredPaged: T9PagedCandidates? = null
    private var t9BulkFilteredMatchedPrefix: String? = null
    private val chineseT9CandidatePipeline = ChineseT9CandidatePipeline(
        characterBudget = { t9HanziCharacterBudget },
        widthBudget = ::t9CandidateWidthBudget,
        candidateMatchesPrefix = { candidate, prefix ->
            service.candidateMatchesT9ResolvedPrefix(candidate, prefix)
        }
    )
    private val t9BulkCandidateLoader = T9BulkCandidateLoader(
        characterBudget = { t9HanziCharacterBudget },
        widthBudget = ::t9CandidateWidthBudget,
        candidateMatchesPrefix = { candidate, prefix ->
            service.candidateMatchesT9ResolvedPrefix(candidate, prefix)
        }
    )
    private var t9ShownUsesLocalBudget = false
    private var t9ShownUsesSmartEnglish = false
    private val t9PinyinRowWindow = T9PinyinRowWindow()
    private var t9RenderedPinyinWindowStart = 0
    private var t9RenderedPinyinItems: List<String> = emptyList()
    private var t9PinyinOptionCount = 0
    private var t9PinyinHasRightOverflow = false
    private var t9PinyinOverflowHintSuppressedByFocus = false
    private var lastRenderedT9Focus = T9CandidateFocus.BOTTOM
    private val t9SmartEnglishPageCache = T9SmartEnglishPageCache(
        characterBudget = { t9HanziCharacterBudget },
        widthBudget = ::t9CandidateWidthBudget
    )
    private val t9RefreshScheduler = T9UiRefreshScheduler(
        postRefresh = { block -> postOnAnimation(block) },
        refreshNow = ::updateUi
    )
    private var lastCandidateRowWidthPx = 0
    private val t9PinyinMeasurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val t9CandidateMeasurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val t9CandidateUiRenderer = T9CandidateUiRenderer(object : T9CandidateUiRenderer.Delegate {
        override fun setPreferAboveCursorAnchor(preferAboveCursorAnchor: Boolean) {
            this@CandidatesView.preferAboveCursorAnchor = preferAboveCursorAnchor
        }

        override fun renderPreedit(panel: FcitxEvent.InputPanelEvent.Data) {
            preeditUi.update(panel)
            preeditUi.root.visibility = if (preeditUi.visible) VISIBLE else GONE
        }

        override fun renderCandidates(
            candidates: FcitxEvent.PagedCandidateEvent.Data,
            orientation: FloatingCandidatesOrientation,
            showShortcutLabels: Boolean
        ) {
            candidatesUi.update(
                candidates,
                orientation,
                showShortcutLabels = showShortcutLabels,
                shortcutLayout = t9ShortcutCandidateLayout().takeIf { showShortcutLabels }
            )
        }

        override fun renderPinyin(pinyinOptions: List<String>, pinyinUseT9: Boolean): Boolean =
            updatePinyinBar(pinyinOptions, pinyinUseT9)

        override fun syncPinyinLayout(): Boolean = syncVisiblePinyinRowLayout()

        override fun renderFocus(focus: T9CandidateFocus) {
            updateT9FocusIndicator(focus)
        }

        override fun showWhenPositioned(contentReady: Boolean) {
            this@CandidatesView.showWhenPositioned(contentReady)
        }

        override fun hideCandidateUi() {
            // RecyclerView won't update its items when ancestor view is GONE.
            showAfterPositioned = false
            visibility = INVISIBLE
        }
    })
    private val t9CandidateUiStateBuilder = T9CandidateUiStateBuilder(object : T9CandidateUiStateBuilder.Delegate {
        override fun getChineseT9InputSnapshot(
            inputPanel: FcitxEvent.InputPanelEvent.Data
        ): ChineseT9InputSnapshot =
            service.getChineseT9InputSnapshot(inputPanel)

        override fun isChineseT9InputModeActive(): Boolean = service.isChineseT9InputModeActive()

        override fun isSmartEnglishT9InputModeActive(): Boolean = service.isSmartEnglishT9InputModeActive()

        override fun getSmartEnglishT9Paged(): FcitxEvent.PagedCandidateEvent.Data? =
            service.getSmartEnglishT9Paged()

        override fun buildSmartEnglishPaged(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates = this@CandidatesView.buildSmartEnglishPaged(data)

        override fun getPendingT9PunctuationPaged(): FcitxEvent.PagedCandidateEvent.Data? =
            service.getPendingT9PunctuationPaged()

        override fun buildT9PendingPunctuationPaged(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates = this@CandidatesView.buildT9PendingPunctuationPaged(data)

        override fun resetT9BulkFilterState() {
            this@CandidatesView.resetT9BulkFilterState()
        }

        override fun requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active: Boolean, prefixes: List<String>) {
            this@CandidatesView.requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active, prefixes)
        }

        override fun filterPagedByT9PinyinPrefixes(
            data: FcitxEvent.PagedCandidateEvent.Data,
            prefixes: List<String>
        ): Pair<T9PagedCandidates, String?> =
            chineseT9CandidatePipeline.filterPagedByPinyinPrefixes(data, prefixes)

        override fun buildLocalBudgetedPagedFromCurrentPage(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates? =
            chineseT9CandidatePipeline.buildLocalBudgetedPagedFromCurrentPage(data)

        override fun resetT9LocalBudgetState() {
            this@CandidatesView.resetT9LocalBudgetState()
        }

        override fun buildT9CursorContextSignature(prefixes: List<String>): String =
            chineseT9CandidatePipeline.buildCursorContextSignature(inputPanel.preedit.toString(), prefixes)

        override fun applyT9HanziCursor(
            data: FcitxEvent.PagedCandidateEvent.Data,
            cursorContextSignature: String
        ): FcitxEvent.PagedCandidateEvent.Data =
            chineseT9CandidatePipeline.applyHanziCursor(data, cursorContextSignature)

        override fun getSmartEnglishT9Presentation(): T9PresentationState? =
            service.getSmartEnglishT9Presentation()

        override fun getT9PresentationState(
            snapshot: ChineseT9InputSnapshot,
            inputPanel: FcitxEvent.InputPanelEvent.Data,
            effectivePaged: FcitxEvent.PagedCandidateEvent.Data
        ): T9PresentationState = service.getT9PresentationState(snapshot, inputPanel, effectivePaged)

        override fun clearHiddenChineseT9CompositionIfCandidateUiSuppressed() {
            service.clearHiddenChineseT9CompositionIfCandidateUiSuppressed()
        }

        override fun effectiveT9CandidateFocus(
            pinyinOptions: List<String>,
            useT9PinyinRow: Boolean
        ): T9CandidateFocus =
            this@CandidatesView.effectiveT9CandidateFocus(pinyinOptions, useT9PinyinRow)
    })
    private var pinyinRowTargetVisible = false

    private val showPaginationArrows by candidatesPrefs.showPaginationArrows
    private val candidatesUi = PagedCandidatesUi(
        ctx, theme, setupTextViewCandidates,
        showPaginationArrows,
        dp(windowRadius),
        onCandidateClick = { shownIndex ->
            if (service.isSmartEnglishT9InputModeActive()) {
                commitSmartEnglishShortcut(shownIndex)
            } else {
                service.moveT9CandidateFocus(T9CandidateFocus.BOTTOM)
                updateT9FocusIndicator()
                selectT9ShownHanziCandidate(shownIndex)
            }
        },
        onPrevPage = {
            if (!offsetT9BottomCandidatePage(-1)) {
                fcitx.launchOnReady { it.offsetCandidatePage(-1) }
            }
        },
        onNextPage = {
            if (!offsetT9BottomCandidatePage(1)) {
                fcitx.launchOnReady { it.offsetCandidatePage(1) }
            }
        }
    )

    /** T9 pinyin selection bar: row above candidates, replaces number row when visible. */
    private val pinyinBarAdapter by lazy {
        T9PinyinChipAdapter(
            context = ctx,
            theme = theme,
            textSizeSp = compactTopRowFontSizeSp,
            horizontalPaddingPx = dpCandidates(itemPaddingHorizontal),
            verticalPaddingPx = dpCandidates(itemPaddingVertical),
            rowHeightPx = pinyinBarRowHeightPx,
            cornerRadiusPx = dpCandidates(windowRadius).toFloat(),
            precreatedChipCount = T9PinyinRowWindow.DEFAULT_MAX_VISIBLE_ITEMS,
            onChipClick = {
                service.commitT9PinyinSelection(it)
                updateT9FocusIndicator()
            }
        )
    }
    private val pinyinBarView = pinyinBarAdapter.root.apply {
        visibility = View.GONE
    }
    private val pinyinOverflowHint = TextView(ctx).apply {
        text = "\u2026"
        textSize = compactTopRowFontSizeSp
        InputUiFont.applyTo(this)
        setTextColor(theme.candidateTextColor)
        alpha = 0.72f
        gravity = Gravity.CENTER
        includeFontPadding = false
        isFocusable = false
        isFocusableInTouchMode = false
        visibility = View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setLineHeight(pinyinBarRowHeightPx)
        }
    }
    private val pinyinRowWrapper = FrameLayout(ctx).apply {
        clipChildren = true
        clipToPadding = true
        addView(pinyinBarView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            pinyinBarRowHeightPx
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
        })
        addView(pinyinOverflowHint, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        })
        setOnClickListener {
            service.moveT9CandidateFocus(T9CandidateFocus.TOP)
            updateT9FocusIndicator()
        }
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        visibility = View.GONE
    }
    private val candidateRowWrapper = FrameLayout(ctx).apply {
        clipChildren = false
        clipToPadding = false
        addView(candidatesUi.root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        setOnClickListener {
            service.moveT9CandidateFocus(T9CandidateFocus.BOTTOM)
            updateT9FocusIndicator()
        }
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
    }

    private fun dpCandidates(value: Int): Int = (value * ctx.resources.displayMetrics.density).toInt()

    /** Height of one lower Hanzi candidate row. Compact top rows are a ratio of this baseline. */
    private val oneCandidateRowHeightPx: Int
        get() {
            val dm = ctx.resources.displayMetrics
            val linePx = (fontSize * dm.scaledDensity * 1.2f).toInt()
            return linePx + 2 * dpCandidates(itemPaddingVertical)
        }

    private val compactTopRowHeightPx: Int
        get() {
            val ratioHeight = oneCandidateRowHeightPx * t9TopBottomRowRatioPercent / 100
            val dm = ctx.resources.displayMetrics
            val fontBounds = (compactTopRowFontSizeSp * dm.scaledDensity * 1.35f).roundToInt()
            return maxOf(ratioHeight, fontBounds + 2 * dpCandidates(itemPaddingVertical))
        }

    /** First compact row: preedit/pinyin preview. */
    private val preeditRowHeightPx: Int get() = compactTopRowHeightPx
    /** Second compact row: pinyin filter chips. */
    private val pinyinBarRowHeightPx: Int get() = compactTopRowHeightPx

    /** Font size for compact top rows so text fits even when the height ratio is small. */
    private val compactTopRowFontSizeSp: Float get() = fontSize * 6f / 11f

    private val pinyinToHanziGapPx: Int get() = dp(T9_PINYIN_TO_HANZI_GAP_DP)

    /** Vertical container for the two bubbles (first row; second+third rows). */
    private val contentWrapper = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(0, 0, 0, dp(12))
    }

    private fun makeBubbleBackground() = GradientDrawable().apply {
        setColor(theme.keyboardColor)
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(windowRadius).toFloat()
    }

    private fun View.applyCandidateBubbleShadow() {
        elevation = dp(7).toFloat()
        translationZ = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineAmbientShadowColor = Color.argb(54, 0, 0, 0)
            outlineSpotShadowColor = Color.argb(78, 0, 0, 0)
        }
    }

    /** Bubble 1: first row only; width = preedit content (left-aligned with bubble 2). */
    private val bubble1Wrapper = FrameLayout(ctx).apply {
        setPadding(dp(windowPadding), dp(windowPadding), dp(windowPadding), dp(windowPadding))
        background = makeBubbleBackground()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        applyCandidateBubbleShadow()
    }

    /** Bubble 2: deterministic T9 rows; pinyin row is top, Hanzi row is explicitly offset below it. */
    private val bubble2Wrapper = FrameLayout(ctx).apply {
        setPadding(dp(windowPadding), dp(windowPadding), dp(windowPadding), dp(windowPadding))
        background = makeBubbleBackground()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipChildren = false
        clipToPadding = false
        applyCandidateBubbleShadow()
    }

    override fun onStartHandleFcitxEvent() {
        val inputPanelData = fcitx.runImmediately { inputPanelCached }
        handleFcitxEvent(FcitxEvent.InputPanelEvent(inputPanelData))
    }

    override fun handleFcitxEvent(it: FcitxEvent<*>) {
        when (it) {
            is FcitxEvent.InputPanelEvent -> {
                inputPanel = it.data
                refreshT9Ui()
            }
            is FcitxEvent.PagedCandidateEvent -> {
                paged = it.data
                chineseT9CandidateLoadingState.onEngineCandidates(
                    data = it.data,
                    compositionKeyCount = service.getT9CompositionKeyCount()
                )
                refreshT9Ui()
            }
            else -> {}
        }
    }

    fun clearTransientState() {
        t9RefreshScheduler.cancel()
        showAfterPositioned = false
        inputPanel = FcitxEvent.InputPanelEvent.Data()
        paged = FcitxEvent.PagedCandidateEvent.Data.Empty
        resetT9BulkFilterState()
        t9ShownUsesSmartEnglish = false
        t9SmartEnglishPageCache.reset()
        chineseT9CandidatePipeline.reset()
        t9CandidateUiRenderer.reset()
        chineseT9CandidateLoadingState.reset()
        preeditUi.update(inputPanel)
        preeditUi.root.visibility = GONE
        t9PinyinRowWindow.clear()
        t9RenderedPinyinWindowStart = 0
        t9RenderedPinyinItems = emptyList()
        t9PinyinOptionCount = 0
        t9PinyinHasRightOverflow = false
        t9PinyinOverflowHintSuppressedByFocus = false
        updatePinyinOverflowHint(false)
        lastRenderedT9Focus = T9CandidateFocus.BOTTOM
        setPinyinRowVisible(false)
        service.moveT9CandidateFocus(T9CandidateFocus.BOTTOM)
        updateT9FocusIndicator()
        candidatesUi.update(paged, orientation)
        visibility = INVISIBLE
    }

    fun prepareForT9CompositionReplay() {
        paged = FcitxEvent.PagedCandidateEvent.Data.Empty
        resetT9BulkFilterState()
        service.moveT9CandidateFocus(T9CandidateFocus.TOP)
        refreshT9Ui()
    }

    fun syncT9CandidateFocus() {
        updateT9FocusIndicator()
    }

    /**
     * Force a full UI refresh. Needed after model-only mutations (pinyin chip selection,
     * delete-reopen) that don't trigger a fcitx event and therefore wouldn't otherwise redraw.
     */
    fun refreshT9Ui() {
        t9RefreshScheduler.requestRefresh()
    }

    fun waitForT9EngineCandidatesThenRefresh() {
        chineseT9CandidateLoadingState.startIfNeeded(
            chineseT9Active = service.isChineseT9InputModeActive(),
            compositionKeyCount = service.getT9CompositionKeyCount()
        )
        refreshT9Ui()
    }

    fun hideT9CandidateUiImmediately() {
        t9RefreshScheduler.cancel()
        showAfterPositioned = false
        t9CandidateUiRenderer.hideImmediately()
    }

    fun getHighlightedT9Pinyin(): String? = t9PinyinRowWindow.highlightedPinyin()

    fun commitT9HanziShortcut(index: Int): Boolean = selectT9ShownHanziCandidate(index)

    fun commitSmartEnglishShortcut(index: Int): Boolean {
        if (!t9ShownUsesSmartEnglish) return false
        val originalIndex = t9ShownOriginalIndices.getOrNull(index) ?: return false
        return service.commitSmartEnglishCandidate(originalIndex)
    }

    fun commitT9PendingPunctuationShortcut(index: Int): Boolean {
        if (!t9ShownUsesPendingPunctuation) return false
        val originalIndex = t9ShownOriginalIndices.getOrNull(index) ?: return false
        return service.commitPendingT9PunctuationCandidate(originalIndex)
    }

    fun getT9PreviewCommitText(): String? {
        if (!service.isChineseT9InputModeActive()) return null
        if (service.getT9CompositionKeyCount() <= 0) return null
        val shown = t9ShownPaged ?: paged
        val snapshot = service.getChineseT9InputSnapshot(inputPanel)
        val preview = service.getT9PresentationState(snapshot, inputPanel, shown)
            .topReading
            ?.toString()
            .orEmpty()
        val commitText = preview
            .filter { it != ' ' && it != '\'' }
            .trim()
        return commitText.takeIf { text ->
            text.isNotEmpty() && text.any { it in 'a'..'z' || it in 'A'..'Z' }
        }
    }

    fun moveHighlightedT9Pinyin(delta: Int): Boolean {
        val state = t9PinyinRowWindow.move(delta) ?: return false
        suppressPinyinOverflowHintForFocus()
        val previousWindowStart = t9RenderedPinyinWindowStart
        renderPinyinWindow(state)
        if (state.windowStart == previousWindowStart) {
            pinyinBarAdapter.scrollToHighlighted()
        } else {
            pinyinBarAdapter.scrollToStart()
        }
        return true
    }

    fun moveHighlightedT9BottomCandidate(delta: Int): Boolean {
        val shown = t9ShownPaged ?: return false
        if (shown.candidates.isEmpty()) return false
        val next = shown.cursorIndex + delta
        return when {
            next in shown.candidates.indices -> {
                if (t9ShownUsesSmartEnglish) {
                    val originalIndex = t9ShownOriginalIndices.getOrNull(next) ?: next
                    return service.setSmartEnglishCandidateIndex(originalIndex)
                }
                t9ShownPaged = chineseT9CandidatePipeline.moveHanziCursor(shown, next) ?: return false
                if (t9ShownUsesPendingPunctuation) {
                    val originalIndex = t9ShownOriginalIndices.getOrNull(next) ?: next
                    service.previewPendingT9PunctuationCandidate(originalIndex)
                }
                refreshT9Ui()
                true
            }
            next >= shown.candidates.size && shown.hasNext -> {
                offsetT9BottomCandidatePage(1)
            }
            next < 0 && shown.hasPrev -> {
                offsetT9BottomCandidatePage(-1)
            }
            else -> false
        }
    }

    fun offsetT9BottomCandidatePage(delta: Int): Boolean {
        if (t9ShownUsesSmartEnglish && t9SmartEnglishPageCache.hasCandidates) {
            if (offsetSmartEnglishCandidatePage(delta)) return true
        }
        if (t9ShownUsesPendingPunctuation && t9PendingPunctuationPager.hasCandidates) {
            if (offsetT9PendingPunctuationCandidatePage(delta)) return true
        }
        if (t9ShownUsesBulkSelection && t9BulkCandidateLoader.hasCandidates) {
            if (offsetT9BulkFilteredCandidatePage(delta)) return true
        }
        if (t9ShownUsesLocalBudget && chineseT9CandidatePipeline.hasLocalBudgetCandidates) {
            if (offsetT9LocalBudgetedCandidatePage(delta)) return true
        }
        val shown = t9ShownPaged ?: return false
        val canOffset = if (delta > 0) shown.hasNext else shown.hasPrev
        if (!canOffset) return false
        fcitx.launchOnReady { it.offsetCandidatePage(delta) }
        return true
    }

    fun commitHighlightedT9BottomCandidate(): Boolean {
        val shown = t9ShownPaged ?: return false
        val shownIndex = shown.cursorIndex
        if (shownIndex !in shown.candidates.indices) return false
        if (t9ShownUsesSmartEnglish) {
            val originalIndex = t9ShownOriginalIndices.getOrNull(shownIndex) ?: shownIndex
            return service.commitSmartEnglishCandidate(originalIndex)
        }
        return selectT9ShownHanziCandidate(shownIndex)
    }

    private fun updateT9FocusIndicator(
        focus: T9CandidateFocus = service.getT9CandidateFocus()
    ) {
        val topFocused = focus == T9CandidateFocus.TOP
        if (topFocused && lastRenderedT9Focus != T9CandidateFocus.TOP) {
            suppressPinyinOverflowHintForFocus()
            t9PinyinRowWindow.resetHighlight()?.let(::renderPinyinWindow)
            pinyinBarAdapter.scrollToStart()
        } else if (!topFocused && t9PinyinOverflowHintSuppressedByFocus) {
            t9PinyinOverflowHintSuppressedByFocus = false
            pinyinRowWrapper.post(::updatePinyinOverflowHintFromLayout)
        }
        pinyinBarAdapter.setHighlightActive(topFocused)
        candidatesUi.setHighlightActive(!topFocused)
        lastRenderedT9Focus = focus
    }

    private fun updateUi() = T9ResponsivenessTrace.measure("CandidatesView.updateUi") {
        buildT9CandidateUiState()?.let { result ->
            applyT9ShownState(result.shownState)
            t9CandidateUiRenderer.render(result.renderState)
        }
    }

    private fun buildT9CandidateUiState(): T9CandidateUiStateBuilder.Result? =
        t9CandidateUiStateBuilder.build(
            T9CandidateUiStateBuilder.Input(
                t9InputModeEnabled = t9InputModeEnabled,
                inputPanel = inputPanel,
                rawPaged = paged,
                orientation = orientation,
                currentlyVisible = visibility == VISIBLE,
                loadingState = chineseT9CandidateLoadingState,
                bulkFilteredPaged = t9BulkFilteredPaged,
                bulkFilteredMatchedPrefix = t9BulkFilteredMatchedPrefix,
                bulkFilterPending = t9BulkCandidateLoader.pending
            )
        )

    private fun applyT9ShownState(shownState: T9CandidateUiStateBuilder.ShownState) {
        t9ShownPaged = shownState.paged
        t9ShownOriginalIndices = shownState.originalIndices
        t9ShownUsesSmartEnglish = shownState.usesSmartEnglish
        t9ShownUsesPendingPunctuation = shownState.usesPendingPunctuation
        t9ShownUsesBulkSelection = shownState.usesBulkSelection
        t9ShownUsesLocalBudget = shownState.usesLocalBudget
        t9ShownMatchedPrefix = shownState.matchedPrefix
    }

    private fun effectiveT9CandidateFocus(
        pinyinOptions: List<String>,
        useT9PinyinRow: Boolean
    ): T9CandidateFocus {
        val current = service.getT9CandidateFocus()
        if (useT9PinyinRow && pinyinOptions.isNotEmpty()) return current
        if (current == T9CandidateFocus.TOP) {
            service.moveT9CandidateFocus(T9CandidateFocus.BOTTOM)
        }
        return T9CandidateFocus.BOTTOM
    }

    private fun shouldShowT9BottomShortcutLabels(
        data: FcitxEvent.PagedCandidateEvent.Data
    ): Boolean =
        data.candidates.isNotEmpty() &&
            (
                t9ShownUsesSmartEnglish ||
                    t9ShownUsesPendingPunctuation ||
                    service.isChineseT9InputModeActive()
            )

    private fun selectT9ShownHanziCandidate(shownIndex: Int): Boolean {
        val shown = t9ShownPaged ?: return false
        if (t9ShownUsesPendingPunctuation) {
            val originalIndex = t9ShownOriginalIndices.getOrNull(shownIndex) ?: shownIndex
            return service.commitPendingT9PunctuationCandidate(originalIndex)
        }
        val originalIndex = t9ShownOriginalIndices.getOrNull(shownIndex) ?: return false
        if (originalIndex < 0) return false
        val selectedCandidate = shown.candidates.getOrNull(shownIndex) ?: return false
        val prefixToConsume = t9ShownMatchedPrefix?.takeIf {
            service.shouldConsumeT9ResolvedPinyinPrefixAfterHanziSelection(it, selectedCandidate)
        }
        fcitx.launchOnReady {
            val selected = if (t9ShownUsesBulkSelection) {
                it.selectFromAll(originalIndex)
            } else {
                it.select(originalIndex)
            }
            if (selected && prefixToConsume != null) {
                post { service.consumeT9ResolvedPinyinPrefix(prefixToConsume) }
            } else if (selected && service.isChineseT9InputModeActive()) {
                post { service.consumeT9PinyinFromSelectedCandidate(selectedCandidate) }
            }
        }
        return true
    }

    private fun offsetT9BulkFilteredCandidatePage(delta: Int): Boolean {
        val page = t9BulkCandidateLoader.offset(delta) ?: return false
        applyT9BulkFilteredPage(page)
        refreshT9Ui()
        return true
    }

    private fun offsetT9LocalBudgetedCandidatePage(delta: Int): Boolean {
        if (!chineseT9CandidatePipeline.offsetLocalBudgetedPage(delta)) return false
        refreshT9Ui()
        return true
    }

    private fun offsetT9PendingPunctuationCandidatePage(delta: Int): Boolean {
        val page = t9PendingPunctuationPager.offset(delta) ?: return false
        applyT9PendingPunctuationPage(page)
        t9ShownPaged?.candidates?.indices?.firstOrNull()?.let { shownIndex ->
            val originalIndex = t9ShownOriginalIndices.getOrNull(shownIndex) ?: return@let
            service.previewPendingT9PunctuationCandidate(originalIndex)
        }
        refreshT9Ui()
        return true
    }

    private fun offsetSmartEnglishCandidatePage(delta: Int): Boolean {
        val page = t9SmartEnglishPageCache.offset(delta) ?: return false
        val nextOriginalIndex = page.candidates.firstOrNull()?.index ?: return false
        return service.setSmartEnglishCandidateIndex(nextOriginalIndex)
    }

    private fun buildSmartEnglishPaged(
        data: FcitxEvent.PagedCandidateEvent.Data
    ): T9PagedCandidates = t9SmartEnglishPageCache.build(data)

    private fun buildT9PendingPunctuationPaged(
        data: FcitxEvent.PagedCandidateEvent.Data
    ): T9PagedCandidates {
        val signature = buildT9CandidateSignature(data)
        t9PendingPunctuationPager.update(
            signature,
            data.candidates.withIndex().toList(),
            t9HanziCharacterBudget,
            t9CandidateWidthBudget()
        )
        val selectedIndex = data.cursorIndex.coerceIn(data.candidates.indices)
        val page = t9PendingPunctuationPager.selectPageContainingOriginalIndex(selectedIndex)
            ?: return T9PagedCandidates.passthrough(data)
        return buildT9PendingPunctuationPagedFromPage(page, selectedIndex)
    }

    private fun applyT9PendingPunctuationPage(page: T9CandidatePager.Page) {
        val shown = buildT9PendingPunctuationPagedFromPage(
            page = page,
            selectedIndex = page.originalIndices.firstOrNull() ?: -1
        )
        t9ShownOriginalIndices = shown.originalIndices
        t9ShownPaged = shown.data
    }

    private fun buildT9PendingPunctuationPagedFromPage(
        page: T9CandidatePager.Page,
        selectedIndex: Int
    ): T9PagedCandidates =
        page.toPagedCandidates(
            layoutHint = paged.layoutHint,
            cursorIndex = page.cursorIndexForOriginalIndex(selectedIndex)
        )

    private fun buildT9CandidateSignature(data: FcitxEvent.PagedCandidateEvent.Data): String =
        T9CandidateSnapshots.pagerContent(data, t9HanziCharacterBudget, t9CandidateWidthBudget())

    private fun applyT9BulkFilteredPage(page: T9CandidatePager.Page) {
        t9BulkFilteredPaged = if (page.candidates.isEmpty()) {
            null
        } else {
            page.toPagedCandidates(
                layoutHint = paged.layoutHint,
                cursorIndex = 0,
            )
        }
    }

    private fun requestT9BulkFilteredCandidatesIfNeeded(
        t9InputModeEnabled: Boolean,
        prefixes: List<String>
    ) {
        if (!t9InputModeEnabled) {
            resetT9BulkFilterState()
            return
        }
        val signature = t9BulkCandidateLoader.requestSignature(prefixes, inputPanel.preedit.toString(), paged.candidates)
        if (!t9BulkCandidateLoader.shouldRequest(signature)) return
        t9BulkCandidateLoader.startRequest(prefixes, signature)
        t9BulkFilteredPaged = null
        t9BulkFilteredMatchedPrefix = null
        fcitx.launchOnReady { api ->
            val rawCandidates = api.getCandidates(0, T9_BULK_FILTER_LIMIT)
            post {
                val result = t9BulkCandidateLoader.finishRequest(signature, rawCandidates.toList(), prefixes)
                    ?: return@post
                t9BulkFilteredMatchedPrefix = result.matchedPrefix
                result.page?.let(::applyT9BulkFilteredPage)
                refreshT9Ui()
            }
        }
    }

    private fun resetT9BulkFilterState() {
        t9BulkFilteredPaged = null
        t9BulkFilteredMatchedPrefix = null
        t9BulkCandidateLoader.reset()
        resetT9LocalBudgetState()
        t9ShownOriginalIndices = intArrayOf()
        t9ShownUsesBulkSelection = false
        t9ShownUsesPendingPunctuation = false
        t9ShownMatchedPrefix = null
    }

    private fun resetT9LocalBudgetState() {
        chineseT9CandidatePipeline.resetLocalBudget()
        t9ShownUsesLocalBudget = false
    }

    private fun showPinyinRowNow() {
        setPinyinRowHeight(pinyinBarRowHeightPx)
        pinyinBarView.visibility = View.VISIBLE
        pinyinRowWrapper.visibility = View.VISIBLE
        schedulePinyinOverflowHintUpdate()
        if (showAfterPositioned) {
            showWhenPositioned(true)
        }
    }

    private fun setPinyinRowVisible(visible: Boolean): Boolean {
        if (!visible &&
            !pinyinRowTargetVisible &&
            pinyinRowWrapper.visibility == View.GONE &&
            pinyinBarView.visibility == View.GONE
        ) {
            return true
        }
        pinyinRowTargetVisible = visible
        if (visible &&
            pinyinRowWrapper.visibility == View.VISIBLE &&
            pinyinBarView.visibility == View.VISIBLE
        ) {
            return syncVisiblePinyinRowLayout()
        }
        pinyinBarView.alpha = 1f
        pinyinBarView.scaleX = 1f
        pinyinBarView.translationY = 0f
        if (visible) {
            val widthReady = syncPinyinRowWidthToCandidates()
            setPinyinRowHeight(pinyinBarRowHeightPx)
            if (widthReady) {
                showPinyinRowNow()
                return true
            } else {
                // Product requirement: the pinyin row must never spill into the Hanzi row. Keep
                // its vertical reservation deterministic even while width is waiting for the
                // candidate row's first measured pass.
                pinyinBarView.visibility = View.INVISIBLE
                pinyinRowWrapper.visibility = View.INVISIBLE
                pinyinRowWrapper.post {
                    if (!pinyinRowTargetVisible) return@post
                    setPinyinRowHeight(pinyinBarRowHeightPx)
                    if (syncPinyinRowWidthToCandidates()) {
                        showPinyinRowNow()
                    }
                }
                return false
            }
        } else {
            pinyinBarAdapter.clear()
            pinyinBarAdapter.scrollToStart()
            t9RenderedPinyinItems = emptyList()
            t9PinyinOptionCount = 0
            t9PinyinHasRightOverflow = false
            t9PinyinOverflowHintSuppressedByFocus = false
            updatePinyinOverflowHint(false)
            pinyinBarView.visibility = View.GONE
            pinyinRowWrapper.visibility = View.GONE
            setPinyinRowHeight(0)
            return true
        }
    }

    private fun syncVisiblePinyinRowLayout(): Boolean {
        if (!pinyinRowTargetVisible) return true
        val widthReady = syncPinyinRowWidthToCandidates()
        setPinyinRowHeight(pinyinBarRowHeightPx)
        if (widthReady && pinyinRowWrapper.visibility == View.INVISIBLE) {
            showPinyinRowNow()
        }
        if (widthReady) {
            schedulePinyinOverflowHintUpdate()
        }
        return widthReady
    }

    private fun setPinyinRowHeight(height: Int) {
        pinyinRowWrapper.minimumHeight = height
        (pinyinBarView.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.height != height) {
                params.height = height
                pinyinBarView.layoutParams = params
            }
        }
        (pinyinRowWrapper.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.height != height) {
                params.height = height
                pinyinRowWrapper.layoutParams = params
            }
        }
        setCandidateRowTopOffset(if (height > 0) height + pinyinToHanziGapPx else 0)
    }

    private fun setCandidateRowTopOffset(offset: Int) {
        (candidateRowWrapper.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.topMargin != offset) {
                params.topMargin = offset
                candidateRowWrapper.layoutParams = params
            }
        }
    }

    private fun syncPinyinRowWidthToCandidates(): Boolean {
        val candidateWidth = firstPositiveCandidateRowWidth()
            ?: lastCandidateRowWidthPx.takeIf { it > 0 }
            ?: return false
        val width = maxOf(candidateWidth, populatedPinyinRowWidthPx() ?: 0)
        (pinyinRowWrapper.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.width != width) {
                params.width = width
                pinyinRowWrapper.layoutParams = params
            }
        }
        return true
    }

    private fun populatedPinyinRowWidthPx(): Int? {
        val visiblePinyin = if (t9PinyinOptionCount > T9_PINYIN_ROW_MIN_VISIBLE_CHIPS) {
            t9RenderedPinyinItems.take(T9_PINYIN_ROW_MIN_VISIBLE_CHIPS)
        } else {
            t9RenderedPinyinItems
        }.takeIf { it.isNotEmpty() }
            ?: return null
        val reservesOverflowHint = shouldReservePinyinOverflowHintSpace()
        val paint = t9PinyinMeasurePaint.apply {
            textSize = compactTopRowFontSizeSp * ctx.resources.displayMetrics.scaledDensity
        }
        val chipPaddingPx = dpCandidates(itemPaddingHorizontal)
        val chipWidthPx = visiblePinyin.withIndex().sumOf { (index, pinyin) ->
            val textWidthPx = paint.measureText(pinyin).roundToInt()
            val rightMarginPx = if (index != visiblePinyin.lastIndex || reservesOverflowHint) chipPaddingPx else 0
            textWidthPx + chipPaddingPx * 2 + rightMarginPx
        }
        // Product decision: the pinyin row should never show a clipped first frame. Short lists
        // reserve their full visible content; long lists reserve four chips plus a quiet ellipsis
        // so the bubble keeps the stable width the user preferred before the layout experiments.
        val overflowHintWidthPx = if (reservesOverflowHint) pinyinOverflowHintReservedWidthPx() else 0
        return (chipWidthPx + overflowHintWidthPx)
            .coerceAtMost(pinyinRowMaxWidthPx())
            .coerceAtLeast(1)
    }

    private fun shouldReservePinyinOverflowHintSpace(): Boolean =
        t9PinyinOptionCount > T9_PINYIN_ROW_MIN_VISIBLE_CHIPS

    private fun pinyinOverflowHintReservedWidthPx(): Int {
        val paint = t9PinyinMeasurePaint.apply {
            textSize = compactTopRowFontSizeSp * ctx.resources.displayMetrics.scaledDensity
        }
        return (paint.measureText("\u2026").roundToInt() + dpCandidates(itemPaddingHorizontal) * 2)
            .coerceAtLeast(dp(T9_PINYIN_ROW_OVERFLOW_HINT_MIN_WIDTH_DP))
    }

    private fun updatePinyinOverflowHint(visible: Boolean) {
        val targetVisibility = if (visible) View.VISIBLE else View.GONE
        if (pinyinOverflowHint.visibility != targetVisibility) {
            pinyinOverflowHint.visibility = targetVisibility
        }
        val rightPadding = when {
            visible -> pinyinOverflowHintReservedWidthPx()
            t9PinyinOverflowHintSuppressedByFocus && pinyinRowTargetVisible ->
                dp(T9_PINYIN_ROW_FOCUSED_END_GAP_DP)
            else -> 0
        }
        if (pinyinBarView.paddingRight != rightPadding) {
            pinyinBarView.setPadding(0, 0, rightPadding, 0)
        }
        pinyinBarView.clipToPadding = rightPadding > 0
    }

    private fun schedulePinyinOverflowHintUpdate() {
        pinyinRowWrapper.post {
            updatePinyinOverflowHintFromLayout()
            // First-show measurement can settle one frame after the row becomes visible; run a
            // second lightweight pass so the overflow hint appears without waiting for focus moves.
            pinyinRowWrapper.post(::updatePinyinOverflowHintFromLayout)
        }
    }

    private fun updatePinyinOverflowHintFromLayout() {
        if (!pinyinRowTargetVisible || pinyinRowWrapper.visibility != View.VISIBLE) {
            updatePinyinOverflowHint(false)
            return
        }
        updatePinyinOverflowHint(
            !t9PinyinOverflowHintSuppressedByFocus && t9PinyinHasRightOverflow
        )
    }

    private fun suppressPinyinOverflowHintForFocus() {
        if (t9PinyinOverflowHintSuppressedByFocus) return
        t9PinyinOverflowHintSuppressedByFocus = true
        updatePinyinOverflowHint(false)
    }

    private fun pinyinRowMaxWidthPx(): Int {
        val parentWidthPx = parentSize[0].roundToInt()
            .takeIf { it > 0 }
            ?: ctx.resources.displayMetrics.widthPixels
        val outerMaxWidthPx = maxWidth
            .takeIf { it > 0 }
            ?: (parentWidthPx - horizontalMarginPx * 2).coerceAtLeast(1)
        return (outerMaxWidthPx - paddingLeft - paddingRight - dp(windowPadding) * 2)
            .coerceAtLeast(1)
    }

    private fun t9ShortcutCandidateLayout(): PagedCandidatesUi.ShortcutLayout {
        val widthBudget = t9CandidateWidthBudget()
        return PagedCandidatesUi.ShortcutLayout(
            maxCandidateWidthPx = widthBudget.maxCandidateWidthPx
        )
    }

    private fun t9CandidateWidthBudget(): T9CandidateWidthBudget {
        val maxWidthPx = pinyinRowMaxWidthPx()
        val itemSpacingPx = dp(candidateItemSpacing)
        val horizontalPaddingPx = dpCandidates(itemPaddingHorizontal)
        val minimumCandidateWidthPx = (fontSize * ctx.resources.displayMetrics.scaledDensity * 1.35f)
            .roundToInt()
            .coerceAtLeast(1)
        val paint = t9CandidateMeasurePaint.apply {
            textSize = fontSize * ctx.resources.displayMetrics.scaledDensity
            InputUiFont.applyTo(this)
        }
        return T9CandidateWidthBudget(
            maxWidthPx = maxWidthPx,
            candidateSpacingPx = itemSpacingPx,
            candidateHorizontalPaddingPx = horizontalPaddingPx,
            minimumCandidateWidthPx = minimumCandidateWidthPx,
            measureTextWidthPx = { text -> paint.measureText(text).roundToInt() }
        )
    }

    private fun firstPositiveCandidateRowWidth(): Int? {
        candidatesUi.root.width.takeIf { it > 0 }?.let { return rememberCandidateRowWidth(it) }
        candidatesUi.root.measuredWidth.takeIf { it > 0 }?.let { return rememberCandidateRowWidth(it) }
        candidateRowWrapper.width.takeIf { it > 0 }?.let { return rememberCandidateRowWidth(it) }
        candidateRowWrapper.measuredWidth.takeIf { it > 0 }?.let { return rememberCandidateRowWidth(it) }
        return null
    }

    private fun rememberCandidateRowWidth(width: Int): Int {
        lastCandidateRowWidthPx = width
        return width
    }

    private fun updatePinyinBar(candidates: List<String>, useT9: Boolean): Boolean {
        if (!useT9) {
            t9PinyinRowWindow.clear()
            t9RenderedPinyinWindowStart = 0
            t9RenderedPinyinItems = emptyList()
            t9PinyinOptionCount = 0
            t9PinyinHasRightOverflow = false
            t9PinyinOverflowHintSuppressedByFocus = false
            updatePinyinOverflowHint(false)
            return setPinyinRowVisible(false)
        }
        if (candidates.isEmpty()) {
            t9PinyinRowWindow.clear()
            t9RenderedPinyinWindowStart = 0
            t9RenderedPinyinItems = emptyList()
            t9PinyinOptionCount = 0
            t9PinyinHasRightOverflow = false
            t9PinyinOverflowHintSuppressedByFocus = false
            updatePinyinOverflowHint(false)
            return setPinyinRowVisible(false)
        }
        t9PinyinOptionCount = candidates.size
        val state = T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.window") {
            t9PinyinRowWindow.submit(candidates)
        }
        val previousWindowStart = t9RenderedPinyinWindowStart
        val ready = renderPinyinWindow(state)
        if (state.windowStart != previousWindowStart) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.scroll") {
                pinyinBarAdapter.scrollToStart()
            }
        }
        return ready
    }

    private fun renderPinyinWindow(state: T9PinyinRowWindow.VisibleState): Boolean {
        t9RenderedPinyinItems = state.items
        t9PinyinHasRightOverflow = state.windowStart + state.items.size < t9PinyinOptionCount
        updatePinyinOverflowHint(t9PinyinHasRightOverflow && !t9PinyinOverflowHintSuppressedByFocus)
        val changed = T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.submit") {
            pinyinBarAdapter.submitList(state.items, state.highlightedIndex)
        }
        schedulePinyinOverflowHintUpdate()
        val ready = T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.visibility") {
            setPinyinRowVisible(true)
        }
        if (changed) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.scroll") {
                pinyinBarAdapter.scrollToStart()
            }
        }
        t9RenderedPinyinWindowStart = state.windowStart
        return ready
    }

    private var bottomInsets = 0

    /** Horizontal gap from screen edge so the bubble doesn't touch left/right. */
    private val horizontalMarginPx: Int get() = dp(horizontalMargin)

    /** Extra bounds reserved for elevation shadows outside the visible bubble surface. */
    private val candidateShadowOutsetPx: Int get() = dp(12)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Enforce maxWidth so total width never exceeds (parent - 2*margin); ViewGroup does not do this by default
        val maxW = if (maxWidth > 0) maxWidth else Int.MAX_VALUE
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val constrainedSize = if (mode == MeasureSpec.UNSPECIFIED) maxW else min(size, maxW)
        val constrainedSpec = MeasureSpec.makeMeasureSpec(constrainedSize, if (mode == MeasureSpec.UNSPECIFIED) MeasureSpec.AT_MOST else mode)
        super.onMeasure(constrainedSpec, heightMeasureSpec)
    }

    private fun showWhenPositioned(contentReady: Boolean) {
        if (!contentReady && visibility != VISIBLE) {
            showAfterPositioned = true
            shouldUpdatePosition = true
            super.setVisibility(INVISIBLE)
            requestLayout()
            invalidate()
            return
        }
        if (visibility == VISIBLE) {
            shouldUpdatePosition = true
            requestLayout()
            invalidate()
            return
        }
        showAfterPositioned = true
        shouldUpdatePosition = true
        super.setVisibility(INVISIBLE)
        requestLayout()
        invalidate()
    }

    private fun updatePosition() {
        val (parentWidth, parentHeight) = parentSize
        val marginPx = horizontalMarginPx
        if (parentWidth > 0) {
            val shadowOutset = candidateShadowOutsetPx
            val windowMargin = (marginPx - shadowOutset).coerceAtLeast(0)
            val maxW = (parentWidth - 2 * windowMargin).toInt().coerceAtLeast(minWidth)
            if (maxWidth != maxW) {
                maxWidth = maxW
                requestLayout()
            }
        }
        if (visibility != VISIBLE && !showAfterPositioned) {
            return
        }
        if (showAfterPositioned && (width <= 0 || height <= 0)) {
            shouldUpdatePosition = true
            requestLayout()
            return
        }
        if (parentWidth <= 0 || parentHeight <= 0) {
            translationX = 0f
            translationY = 0f
            return
        }
        val (_horizontal, bottom, top) = anchorPosition
        val w: Int = width
        val h: Int = height
        val selfHeight = h.toFloat()
        val tX: Float = (marginPx - candidateShadowOutsetPx).coerceAtLeast(0).toFloat()
        val bottomLimit = parentHeight - bottomInsets
        val bottomSpace = bottomLimit - bottom
        // move CandidatesView above cursor anchor, only when
        val tY: Float = if (preferAboveCursorAnchor) {
            val maxY = (bottomLimit - selfHeight).coerceAtLeast(0f)
            (top - selfHeight).coerceIn(0f, maxY)
        } else if (
            bottom + selfHeight > bottomLimit   // bottom space is not enough
            && top > bottomSpace                // top space is larger than bottom
        ) {
            top - selfHeight
        } else {
            bottom
        }
        translationX = tX
        translationY = tY
        if (showAfterPositioned) {
            showAfterPositioned = false
            super.setVisibility(VISIBLE)
        }
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
        val shadowOutset = candidateShadowOutsetPx
        setPadding(shadowOutset, 0, shadowOutset, shadowOutset)
        contentWrapper.translationX = -(shadowOutset - horizontalMarginPx).coerceAtLeast(0).toFloat()
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(Color.TRANSPARENT)
        // Two bubbles: bubble1 = first row (width by pinyin), bubble2 = second + third rows

        bubble1Wrapper.addView(preeditUi.root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            preeditRowHeightPx
        ).apply { gravity = Gravity.START or Gravity.TOP })

        // Pinyin bar: width 0 so bubble2 width is driven only by candidates; we sync width after layout.
        bubble2Wrapper.addView(pinyinRowWrapper, FrameLayout.LayoutParams(
            0,
            pinyinBarRowHeightPx
        ).apply {
            gravity = Gravity.START or Gravity.TOP
        })
        bubble2Wrapper.addView(candidateRowWrapper, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.START or Gravity.TOP })
        candidatesUi.root.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                outRect.right = dp(candidateItemSpacing)
            }
        })
        // Bubble 1: width by pinyin content, left-aligned
        contentWrapper.addView(bubble1Wrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.START or Gravity.TOP })
        // Bubble 2: width by content (candidates row), right edge at content width within margin
        contentWrapper.addView(bubble2Wrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            topMargin = dp(bubbleGap)
        })

        add(contentWrapper, lParams(matchParent, wrapContent) {
            topOfParent()
            startOfParent()
        })

        // Layout can discover the candidate row width after the first render, but it must still
        // respect the pinyin usability floor or the row flashes wide and immediately shrinks again.
        bubble2Wrapper.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val cw = candidatesUi.root.width
                if (cw > 0) {
                    lastCandidateRowWidthPx = cw
                }
                val targetWidth = cw.takeIf { it > 0 }
                    ?.let { maxOf(it, populatedPinyinRowWidthPx() ?: 0) }
                    ?: return
                if ((pinyinRowWrapper.layoutParams as? FrameLayout.LayoutParams)?.width != targetWidth) {
                    (pinyinRowWrapper.layoutParams as? FrameLayout.LayoutParams)?.width = targetWidth
                    pinyinRowWrapper.requestLayout()
                }
                if (pinyinRowTargetVisible &&
                    pinyinRowWrapper.visibility == View.INVISIBLE
                ) {
                    showPinyinRowNow()
                }
            }
        })

        isFocusable = false
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
            showAfterPositioned = false
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

    companion object {
        private const val T9_BULK_FILTER_LIMIT = 80
        private const val T9_PINYIN_TO_HANZI_GAP_DP = 2
        private const val T9_PINYIN_ROW_MIN_VISIBLE_CHIPS = 4
        private const val T9_PINYIN_ROW_OVERFLOW_HINT_MIN_WIDTH_DP = 18
        private const val T9_PINYIN_ROW_FOCUSED_END_GAP_DP = 10
    }
}

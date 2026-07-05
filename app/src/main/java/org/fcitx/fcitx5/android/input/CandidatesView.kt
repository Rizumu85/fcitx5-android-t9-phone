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
import org.fcitx.fcitx5.android.input.t9.ChineseT9CandidateLoadingState
import org.fcitx.fcitx5.android.input.t9.ChineseT9InputSnapshot
import org.fcitx.fcitx5.android.input.t9.T9BulkCandidateLoader
import org.fcitx.fcitx5.android.input.t9.T9CandidateBudget
import org.fcitx.fcitx5.android.input.t9.T9CandidateFocus
import org.fcitx.fcitx5.android.input.t9.T9CandidatePager
import org.fcitx.fcitx5.android.input.t9.T9CandidateRowMeasurement
import org.fcitx.fcitx5.android.input.t9.T9CandidateSnapshots
import org.fcitx.fcitx5.android.input.t9.T9CandidateSurfaceLayout
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiSnapshotPipeline
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiStateBuilder
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiRenderer
import org.fcitx.fcitx5.android.input.t9.T9CandidateWidthBudget
import org.fcitx.fcitx5.android.input.t9.T9PagedCandidates
import org.fcitx.fcitx5.android.input.t9.T9PinyinChipAdapter
import org.fcitx.fcitx5.android.input.t9.T9PinyinOverflowPolicy
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowRenderPlanner
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowVisibilityPlanner
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowWidthCalculator
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowWindow
import org.fcitx.fcitx5.android.input.t9.T9PresentationState
import org.fcitx.fcitx5.android.input.t9.T9ResponsivenessTrace
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
import kotlin.math.ceil
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
    private var t9BulkFilteredPaged: T9PagedCandidates? = null
    private var t9BulkFilteredMatchedPrefix: String? = null
    private val t9BulkCandidateLoader = T9BulkCandidateLoader(
        characterBudget = { t9HanziCharacterBudget },
        widthBudget = ::t9CandidateWidthBudget,
        candidateMatchesPrefix = { candidate, prefix ->
            service.candidateMatchesT9ResolvedPrefix(candidate, prefix)
        }
    )
    private var t9ShownUsesLocalBudget = false
    private var t9RenderedPinyinWindowStart = 0
    private var t9RenderedPinyinItems: List<String> = emptyList()
    private var t9RenderedPinyinUsesWindowedDisplay = false
    private var lastRenderedT9Focus = T9CandidateFocus.BOTTOM
    private val t9CandidateUiSnapshotPipeline = T9CandidateUiSnapshotPipeline(
        characterBudget = { t9HanziCharacterBudget },
        widthBudget = ::t9CandidateWidthBudget,
        candidateMatchesPrefix = { candidate, prefix ->
            service.candidateMatchesT9ResolvedPrefix(candidate, prefix)
        }
    )
    private val t9RefreshScheduler = T9UiRefreshScheduler(
        postRefresh = { block -> postOnAnimation(block) },
        refreshNow = ::updateUi
    )
    private val t9CandidateRowMeasurement = T9CandidateRowMeasurement()
    private val t9PinyinMeasurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val t9CandidateMeasurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val t9CandidateUiRenderer = T9CandidateUiRenderer(object : T9CandidateUiRenderer.Delegate {
        override fun setPreferAboveCursorAnchor(preferAboveCursorAnchor: Boolean) {
            this@CandidatesView.preferAboveCursorAnchor = preferAboveCursorAnchor
        }

        override fun renderPreedit(panel: FcitxEvent.InputPanelEvent.Data, reserveRow: Boolean) {
            preeditUi.update(panel)
            preeditUi.root.visibility = when {
                preeditUi.visible -> VISIBLE
                reserveRow -> INVISIBLE
                else -> GONE
            }
        }

        override fun renderCandidates(
            candidates: FcitxEvent.PagedCandidateEvent.Data,
            orientation: FloatingCandidatesOrientation,
            showShortcutLabels: Boolean
        ) {
            val rowSignature = candidateRowWidthSignature(candidates, orientation, showShortcutLabels)
            if (t9CandidateRowMeasurement.markContent(rowSignature)) {
                if (pinyinRowTargetVisible && t9RenderedPinyinItems.isNotEmpty()) {
                    pinyinBarView.visibility = View.INVISIBLE
                    pinyinRowWrapper.visibility = View.INVISIBLE
                }
            }
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
        ): T9PagedCandidates = t9CandidateUiSnapshotPipeline.buildSmartEnglishPaged(data)

        override fun getPendingT9PunctuationPaged(): FcitxEvent.PagedCandidateEvent.Data? =
            service.getPendingT9PunctuationPaged()

        override fun buildT9PendingPunctuationPaged(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates = t9CandidateUiSnapshotPipeline.buildPendingPunctuationPaged(data)

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
            t9CandidateUiSnapshotPipeline.filterChinesePagedByPinyinPrefixes(data, prefixes)

        override fun buildLocalBudgetedPagedFromCurrentPage(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates? =
            t9CandidateUiSnapshotPipeline.buildChineseLocalBudgetedPagedFromCurrentPage(data)

        override fun resetT9LocalBudgetState() {
            this@CandidatesView.resetT9LocalBudgetState()
        }

        override fun buildT9CursorContextSignature(prefixes: List<String>): String =
            t9CandidateUiSnapshotPipeline.buildChineseCursorContextSignature(inputPanel.preedit.toString(), prefixes)

        override fun applyT9HanziCursor(
            data: FcitxEvent.PagedCandidateEvent.Data,
            cursorContextSignature: String
        ): FcitxEvent.PagedCandidateEvent.Data =
            t9CandidateUiSnapshotPipeline.applyChineseHanziCursor(data, cursorContextSignature)

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
        setTextColor(theme.candidateCommentColor)
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
        t9CandidateUiSnapshotPipeline.reset()
        t9CandidateUiRenderer.reset()
        chineseT9CandidateLoadingState.reset()
        t9CandidateRowMeasurement.clear()
        preeditUi.update(inputPanel)
        preeditUi.root.visibility = GONE
        t9CandidateUiSnapshotPipeline.clearPinyinWindow()
        t9RenderedPinyinWindowStart = 0
        t9RenderedPinyinItems = emptyList()
        t9RenderedPinyinUsesWindowedDisplay = false
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

    fun getHighlightedT9Pinyin(): String? = t9CandidateUiSnapshotPipeline.highlightedPinyin()

    fun commitT9HanziShortcut(index: Int): Boolean = selectT9ShownHanziCandidate(index)

    fun commitSmartEnglishShortcut(index: Int): Boolean {
        val originalIndex = t9CandidateUiSnapshotPipeline.smartEnglishShortcutOriginalIndex(index)
            ?: return false
        return service.commitSmartEnglishCandidate(originalIndex)
    }

    fun commitT9PendingPunctuationShortcut(index: Int): Boolean {
        val originalIndex = t9CandidateUiSnapshotPipeline.pendingPunctuationShortcutOriginalIndex(index)
            ?: return false
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
        val state = t9CandidateUiSnapshotPipeline.movePinyinWindow(delta) ?: return false
        renderPinyinWindow(state)
        if (t9RenderedPinyinUsesWindowedDisplay) {
            pinyinBarAdapter.scrollToStart()
        } else {
            pinyinBarAdapter.scrollToHighlighted(pinyinRowViewportWidthPx())
        }
        return true
    }

    fun moveHighlightedT9BottomCandidate(delta: Int): Boolean {
        handleSnapshotPipelineMove(delta)?.let { return it }
        if (t9CandidateUiSnapshotPipeline.ownsCurrentShownState) return false
        val shown = t9ShownPaged ?: return false
        if (shown.candidates.isEmpty()) return false
        val next = shown.cursorIndex + delta
        return when {
            next in shown.candidates.indices -> {
                t9ShownPaged = t9CandidateUiSnapshotPipeline.moveChineseHanziCursor(shown, next) ?: return false
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
        handleSnapshotPipelinePageOffset(delta)?.let { return it }
        if (t9CandidateUiSnapshotPipeline.ownsCurrentShownState) return false
        if (t9ShownUsesBulkSelection && t9BulkCandidateLoader.hasCandidates) {
            if (offsetT9BulkFilteredCandidatePage(delta)) return true
        }
        if (t9ShownUsesLocalBudget && t9CandidateUiSnapshotPipeline.hasChineseLocalBudgetCandidates) {
            if (offsetT9LocalBudgetedCandidatePage(delta)) return true
        }
        val shown = t9ShownPaged ?: return false
        val canOffset = if (delta > 0) shown.hasNext else shown.hasPrev
        if (!canOffset) return false
        fcitx.launchOnReady { it.offsetCandidatePage(delta) }
        return true
    }

    fun commitHighlightedT9BottomCandidate(): Boolean {
        handleSnapshotPipelineCommit()?.let { return it }
        if (t9CandidateUiSnapshotPipeline.ownsCurrentShownState) return false
        val shown = t9ShownPaged ?: return false
        val shownIndex = shown.cursorIndex
        if (shownIndex !in shown.candidates.indices) return false
        return selectT9ShownHanziCandidate(shownIndex)
    }

    private fun handleSnapshotPipelineMove(delta: Int): Boolean? =
        when (val result = t9CandidateUiSnapshotPipeline.moveCurrentBottomCandidate(delta)) {
            is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.SmartEnglish ->
                service.setSmartEnglishCandidateIndex(result.nextOriginalIndex)
            is T9CandidateUiSnapshotPipeline.MoveBottomCandidate.PendingPunctuation -> {
                applySnapshotPipelineShownPage(result.shown)
                service.previewPendingT9PunctuationCandidate(result.previewOriginalIndex)
                refreshT9Ui()
                true
            }
            null -> null
        }

    private fun handleSnapshotPipelinePageOffset(delta: Int): Boolean? =
        when (val result = t9CandidateUiSnapshotPipeline.offsetCurrentPage(delta)) {
            is T9CandidateUiSnapshotPipeline.PageOffset.SmartEnglish ->
                service.setSmartEnglishCandidateIndex(result.nextOriginalIndex)
            is T9CandidateUiSnapshotPipeline.PageOffset.PendingPunctuation -> {
                applySnapshotPipelineShownPage(result.shown)
                result.previewOriginalIndex?.let(service::previewPendingT9PunctuationCandidate)
                refreshT9Ui()
                true
            }
            null -> null
        }

    private fun handleSnapshotPipelineCommit(): Boolean? =
        when (val result = t9CandidateUiSnapshotPipeline.commitCurrentBottomCandidate()) {
            is T9CandidateUiSnapshotPipeline.CommitBottomCandidate.SmartEnglish ->
                service.commitSmartEnglishCandidate(result.originalIndex)
            is T9CandidateUiSnapshotPipeline.CommitBottomCandidate.PendingPunctuation ->
                service.commitPendingT9PunctuationCandidate(result.originalIndex)
            null -> null
        }

    private fun applySnapshotPipelineShownPage(shown: T9PagedCandidates) {
        t9ShownPaged = shown.data
    }

    private fun updateT9FocusIndicator(
        focus: T9CandidateFocus = service.getT9CandidateFocus()
    ) {
        val topFocused = focus == T9CandidateFocus.TOP
        if (topFocused && lastRenderedT9Focus != T9CandidateFocus.TOP) {
            updatePinyinOverflowHint(false)
            t9CandidateUiSnapshotPipeline.resetPinyinHighlight()?.let(::renderPinyinWindow)
            pinyinBarAdapter.scrollToStart()
        } else if (!topFocused) {
            t9CandidateUiSnapshotPipeline.currentPinyinWindowState()?.let(::renderPinyinWindow)
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
        val snapshot = t9CandidateUiSnapshotPipeline.updateShownState(
            paged = shownState.paged,
            originalIndices = shownState.originalIndices,
            usesSmartEnglish = shownState.usesSmartEnglish,
            usesPendingPunctuation = shownState.usesPendingPunctuation
        )
        t9ShownPaged = shownState.paged
        t9ShownOriginalIndices = if (snapshot.ownsPagingState) {
            intArrayOf()
        } else {
            shownState.originalIndices
        }
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

    private fun selectT9ShownHanziCandidate(shownIndex: Int): Boolean {
        val shown = t9ShownPaged ?: return false
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
        if (!t9CandidateUiSnapshotPipeline.offsetChineseLocalBudgetedPage(delta)) return false
        refreshT9Ui()
        return true
    }

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
        t9ShownMatchedPrefix = null
    }

    private fun resetT9LocalBudgetState() {
        t9CandidateUiSnapshotPipeline.resetChineseLocalBudgetState()
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
        val snapshot = pinyinRowVisibilitySnapshot()
        val rowAlreadyVisible =
            snapshot.wrapperVisibility == T9PinyinRowVisibilityPlanner.Visibility.VISIBLE &&
                snapshot.barVisibility == T9PinyinRowVisibilityPlanner.Visibility.VISIBLE
        val widthReady = visible && !rowAlreadyVisible && syncPinyinRowWidthToCandidates()
        val action = T9PinyinRowVisibilityPlanner.planSetVisible(
            requestedVisible = visible,
            snapshot = snapshot,
            widthReady = widthReady
        )
        if (action == T9PinyinRowVisibilityPlanner.SetVisibleAction.NOOP_READY) return true
        pinyinRowTargetVisible = visible

        return when (action) {
            T9PinyinRowVisibilityPlanner.SetVisibleAction.NOOP_READY -> true
            T9PinyinRowVisibilityPlanner.SetVisibleAction.SYNC_VISIBLE_LAYOUT ->
                syncVisiblePinyinRowLayout()
            T9PinyinRowVisibilityPlanner.SetVisibleAction.SHOW_NOW -> {
                resetPinyinRowTransform()
                setPinyinRowHeight(pinyinBarRowHeightPx)
                showPinyinRowNow()
                true
            }
            T9PinyinRowVisibilityPlanner.SetVisibleAction.WAIT_FOR_WIDTH -> {
                resetPinyinRowTransform()
                setPinyinRowHeight(pinyinBarRowHeightPx)
                // Product requirement: the pinyin row must never spill into the Hanzi row. Keep
                // its vertical reservation deterministic even while width is waiting for the
                // candidate row's first measured pass.
                pinyinBarView.visibility = View.INVISIBLE
                pinyinRowWrapper.visibility = View.INVISIBLE
                pinyinRowWrapper.post {
                    when (T9PinyinRowVisibilityPlanner.planDeferredWidth(
                        targetVisible = pinyinRowTargetVisible,
                        widthReady = pinyinRowTargetVisible && syncPinyinRowWidthToCandidates()
                    )) {
                        T9PinyinRowVisibilityPlanner.DeferredWidthAction.SHOW_NOW -> {
                            setPinyinRowHeight(pinyinBarRowHeightPx)
                            showPinyinRowNow()
                        }
                        T9PinyinRowVisibilityPlanner.DeferredWidthAction.KEEP_WAITING ->
                            setPinyinRowHeight(pinyinBarRowHeightPx)
                        T9PinyinRowVisibilityPlanner.DeferredWidthAction.IGNORE -> Unit
                    }
                }
                false
            }
            T9PinyinRowVisibilityPlanner.SetVisibleAction.HIDE_NOW -> {
                pinyinBarAdapter.clear()
                pinyinBarAdapter.scrollToStart()
                t9RenderedPinyinItems = emptyList()
                t9RenderedPinyinUsesWindowedDisplay = false
                updatePinyinOverflowHint(false)
                pinyinBarView.visibility = View.GONE
                pinyinRowWrapper.visibility = View.GONE
                setPinyinRowHeight(0)
                true
            }
        }
    }

    private fun resetPinyinRowTransform() {
        pinyinBarView.alpha = 1f
        pinyinBarView.scaleX = 1f
        pinyinBarView.translationY = 0f
    }

    private fun pinyinRowVisibilitySnapshot(): T9PinyinRowVisibilityPlanner.Snapshot =
        T9PinyinRowVisibilityPlanner.Snapshot(
            targetVisible = pinyinRowTargetVisible,
            wrapperVisibility = pinyinRowWrapper.visibility.toT9PinyinRowVisibility(),
            barVisibility = pinyinBarView.visibility.toT9PinyinRowVisibility()
        )

    private fun Int.toT9PinyinRowVisibility(): T9PinyinRowVisibilityPlanner.Visibility =
        when (this) {
            View.VISIBLE -> T9PinyinRowVisibilityPlanner.Visibility.VISIBLE
            View.INVISIBLE -> T9PinyinRowVisibilityPlanner.Visibility.INVISIBLE
            else -> T9PinyinRowVisibilityPlanner.Visibility.GONE
        }

    private fun syncVisiblePinyinRowLayout(): Boolean {
        if (!pinyinRowTargetVisible) return true
        t9CandidateUiSnapshotPipeline.currentPinyinWindowState()?.let {
            renderPinyinWindow(
                state = it,
                candidateRowWidthPx = null,
                scheduleLayoutCheck = false,
                updateVisibility = false
            )
        }
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
        val plan = currentSurfaceLayoutPlan()
            ?.takeIf { it.contentReady }
            ?: return false
        val width = plan.rowWidthPx ?: return false
        setPinyinRowWidth(width)
        return true
    }

    private fun setPinyinRowWidth(width: Int) {
        (pinyinBarView.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.width != width) {
                params.width = width
                pinyinBarView.layoutParams = params
            }
        }
        (pinyinRowWrapper.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.width != width) {
                params.width = width
                pinyinRowWrapper.layoutParams = params
            }
        }
    }

    private fun pinyinRowWidths(): T9PinyinRowWidthCalculator.Widths? {
        val visiblePinyin = t9RenderedPinyinItems.takeIf { it.isNotEmpty() }
            ?: return null
        return T9PinyinRowWidthCalculator.calculate(
            T9PinyinRowWidthCalculator.Input(
                items = visiblePinyin,
                minVisibleChips = T9_PINYIN_ROW_MIN_VISIBLE_CHIPS,
                chipHorizontalPaddingPx = dpCandidates(itemPaddingHorizontal),
                overflowHintWidthPx = pinyinOverflowHintReservedWidthPx(),
                foldedEdgeSafetyPx = dp(T9_PINYIN_ROW_FOLDED_EDGE_SAFETY_DP),
                measureTextWidthPx = ::pinyinTextWidthPx
            )
        )
    }

    private fun pinyinRowDesiredWidthPx(candidateRowWidthPx: Int? = null): Int? =
        currentSurfaceLayoutPlan(candidateRowWidthPx)
            ?.takeIf { it.contentReady }
            ?.rowWidthPx

    private fun pinyinTextWidthPx(text: String): Int {
        val paint = t9PinyinMeasurePaint.apply {
            textSize = compactTopRowFontSizeSp * ctx.resources.displayMetrics.scaledDensity
            InputUiFont.applyTo(this)
        }
        return ceil(paint.measureText(text).toDouble()).toInt()
    }

    private fun pinyinChipWidthsPx(items: List<String>): List<Int> {
        val chipPaddingPx = dpCandidates(itemPaddingHorizontal)
        return items.map { pinyin ->
            pinyinTextWidthPx(pinyin) + chipPaddingPx * 2
        }
    }

    private fun currentSurfaceLayoutPlan(
        candidateRowWidthPx: Int? = null
    ): T9CandidateSurfaceLayout.Plan? {
        if (t9RenderedPinyinItems.isEmpty()) return null
        val widths = pinyinRowWidths() ?: return null
        return T9CandidateSurfaceLayout.plan(
            T9CandidateSurfaceLayout.Input(
                candidateMeasuredWidthPx = candidateRowWidthPx ?: currentCandidateRowWidthForPinyinPolicy(),
                pinyinCount = t9RenderedPinyinItems.size,
                pinyinFullContentWidthPx = widths.fullContentWidthPx,
                pinyinFoldedContentWidthPx = widths.foldedContentWidthPx,
                maxRowWidthPx = pinyinRowMaxWidthPx(),
                minVisiblePinyinChips = T9_PINYIN_ROW_MIN_VISIBLE_CHIPS,
                pinyinRowFocused = service.getT9CandidateFocus() == T9CandidateFocus.TOP
            )
        )
    }

    private fun currentPinyinRowPlan(candidateRowWidthPx: Int? = null): T9PinyinOverflowPolicy.Plan? =
        currentSurfaceLayoutPlan(candidateRowWidthPx)?.pinyin

    private fun currentCandidateRowWidthForPinyinPolicy(): Int? =
        t9CandidateRowMeasurement.currentWidthPx()

    private fun pinyinRowViewportWidthPx(): Int? {
        pinyinRowWrapper.width.takeIf { it > 0 }?.let { return it }
        pinyinRowWrapper.measuredWidth.takeIf { it > 0 }?.let { return it }
        (pinyinRowWrapper.layoutParams as? FrameLayout.LayoutParams)
            ?.width
            ?.takeIf { it > 0 }
            ?.let { return it }
        return null
    }

    private fun pinyinOverflowHintReservedWidthPx(): Int {
        val paint = t9PinyinMeasurePaint.apply {
            textSize = compactTopRowFontSizeSp * ctx.resources.displayMetrics.scaledDensity
        }
        return (ceil(paint.measureText("\u2026").toDouble()).toInt() + dpCandidates(itemPaddingHorizontal) * 2)
            .coerceAtLeast(dp(T9_PINYIN_ROW_OVERFLOW_HINT_MIN_WIDTH_DP))
    }

    private fun updatePinyinOverflowHint(visible: Boolean) {
        val targetVisibility = if (visible) View.VISIBLE else View.GONE
        if (pinyinOverflowHint.visibility != targetVisibility) {
            pinyinOverflowHint.visibility = targetVisibility
        }
        val rightPadding = when {
            visible -> pinyinOverflowHintReservedWidthPx()
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
        val state = t9CandidateUiSnapshotPipeline.currentPinyinWindowState()
        if (state == null) {
            updatePinyinOverflowHint(false)
            return
        }
        renderPinyinWindow(
            state = state,
            candidateRowWidthPx = currentCandidateRowWidthForPinyinPolicy(),
            scheduleLayoutCheck = false,
            updateVisibility = false
        )
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

    private fun candidateRowWidthSignature(
        candidates: FcitxEvent.PagedCandidateEvent.Data,
        orientation: FloatingCandidatesOrientation,
        showShortcutLabels: Boolean
    ): String =
        T9CandidateSnapshots.renderCandidates(
            data = candidates,
            orientation = orientation,
            showShortcutLabels = showShortcutLabels
        ).contentSignature

    private fun updatePinyinBar(candidates: List<String>, useT9: Boolean): Boolean {
        if (!useT9) {
            t9CandidateUiSnapshotPipeline.clearPinyinWindow()
            t9RenderedPinyinWindowStart = 0
            t9RenderedPinyinItems = emptyList()
            t9RenderedPinyinUsesWindowedDisplay = false
            updatePinyinOverflowHint(false)
            return setPinyinRowVisible(false)
        }
        if (candidates.isEmpty()) {
            t9CandidateUiSnapshotPipeline.clearPinyinWindow()
            t9RenderedPinyinWindowStart = 0
            t9RenderedPinyinItems = emptyList()
            t9RenderedPinyinUsesWindowedDisplay = false
            updatePinyinOverflowHint(false)
            return setPinyinRowVisible(false)
        }
        val state = T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.window") {
            t9CandidateUiSnapshotPipeline.submitPinyinWindow(candidates)
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

    private fun renderPinyinWindow(state: T9PinyinRowWindow.VisibleState): Boolean =
        renderPinyinWindow(
            state = state,
            candidateRowWidthPx = null,
            scheduleLayoutCheck = true,
            updateVisibility = true
        )

    private fun renderPinyinWindow(
        state: T9PinyinRowWindow.VisibleState,
        candidateRowWidthPx: Int?,
        scheduleLayoutCheck: Boolean,
        updateVisibility: Boolean
    ): Boolean {
        t9RenderedPinyinItems = state.items
        val surfacePlan = currentSurfaceLayoutPlan(candidateRowWidthPx)
        val rowPlan = surfacePlan?.pinyin
        val renderPlan = T9PinyinRowRenderPlanner.plan(
            state = state,
            rowPlan = rowPlan,
            focusedViewportWidthPx = surfacePlan?.rowWidthPx ?: pinyinRowViewportWidthPx(),
            focusedEdgeGuardPx = dp(T9_PINYIN_ROW_FOCUSED_EDGE_GUARD_DP),
            chipWidthsPx = pinyinChipWidthsPx(state.items),
            chipSpacingPx = dpCandidates(itemPaddingHorizontal)
        )
        t9RenderedPinyinUsesWindowedDisplay = renderPlan.usesWindowedDisplay
        updatePinyinOverflowHint(renderPlan.showOverflowHint)
        val changed = T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.submit") {
            pinyinBarAdapter.submitList(renderPlan.displayedItems, renderPlan.displayedHighlight)
        }
        if (scheduleLayoutCheck) {
            schedulePinyinOverflowHintUpdate()
        }
        val ready = if (updateVisibility) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderPinyin.visibility") {
                setPinyinRowVisible(true)
            }
        } else {
            true
        }
        if (changed && scheduleLayoutCheck) {
            // Product decision: folded pinyin chips must look stable without exposing a fifth chip.
            // The first frame uses text estimates; this pass lets real TextView widths correct
            // custom-font/fallback glyph differences before the user has to move focus.
            pinyinRowWrapper.post {
                if (pinyinRowTargetVisible) {
                    syncVisiblePinyinRowLayout()
                }
            }
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
                    t9CandidateRowMeasurement.remember(cw)
                }
                if (pinyinRowWrapper.visibility == View.VISIBLE) {
                    return
                }
                val targetWidth = currentCandidateRowWidthForPinyinPolicy()
                    ?.let { maxOf(it, pinyinRowDesiredWidthPx(it) ?: 0) }
                    ?: return
                val widthChanged =
                    (pinyinRowWrapper.layoutParams as? FrameLayout.LayoutParams)?.width != targetWidth ||
                    (pinyinBarView.layoutParams as? FrameLayout.LayoutParams)?.width != targetWidth
                when (T9PinyinRowVisibilityPlanner.planLayoutPass(
                    targetVisible = pinyinRowTargetVisible,
                    wrapperVisibility = pinyinRowWrapper.visibility.toT9PinyinRowVisibility(),
                    rowVisible = false,
                    widthChanged = widthChanged,
                    widthReady = true
                )) {
                    T9PinyinRowVisibilityPlanner.LayoutPassAction.APPLY_WIDTH -> {
                        setPinyinRowWidth(targetWidth)
                        schedulePinyinOverflowHintUpdate()
                    }
                    T9PinyinRowVisibilityPlanner.LayoutPassAction.SHOW_WAITING_ROW -> {
                        if (widthChanged) {
                            setPinyinRowWidth(targetWidth)
                        }
                        showPinyinRowNow()
                    }
                    T9PinyinRowVisibilityPlanner.LayoutPassAction.NONE -> Unit
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
        private const val T9_PINYIN_ROW_FOLDED_EDGE_SAFETY_DP = 8
        private const val T9_PINYIN_ROW_FOCUSED_EDGE_GUARD_DP = 8
    }
}

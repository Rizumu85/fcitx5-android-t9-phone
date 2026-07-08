/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.graphics.Color
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
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation
import org.fcitx.fcitx5.android.input.candidates.floating.PagedCandidatesUi
import org.fcitx.fcitx5.android.input.candidates.floating.T9ShortcutCandidatesUi
import org.fcitx.fcitx5.android.input.preedit.PreeditUi
import org.fcitx.fcitx5.android.input.t9.ChineseT9CandidateLoadingState
import org.fcitx.fcitx5.android.input.t9.ChineseT9CandidatePipeline
import org.fcitx.fcitx5.android.input.t9.ChineseT9InputSnapshot
import org.fcitx.fcitx5.android.input.t9.ChineseT9PresentationSnapshotKey
import org.fcitx.fcitx5.android.input.t9.T9CandidateBudget
import org.fcitx.fcitx5.android.input.t9.T9CandidateFocus
import org.fcitx.fcitx5.android.input.t9.T9CandidateInteractionController
import org.fcitx.fcitx5.android.input.t9.T9CandidateSurfaceAndroidAdapter
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiInputSnapshot
import org.fcitx.fcitx5.android.input.t9.T9CandidateSurfacePlanner
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiSnapshot
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiSnapshotPipeline
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiStateBuilder
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiRenderer
import org.fcitx.fcitx5.android.input.t9.T9CandidateWidthBudget
import org.fcitx.fcitx5.android.input.t9.T9PagedCandidates
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowAndroidAdapter
import org.fcitx.fcitx5.android.input.t9.T9PinyinChipAdapter
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowSurfacePlanner
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowWidthCalculator
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowWindow
import org.fcitx.fcitx5.android.input.t9.T9PresentationState
import org.fcitx.fcitx5.android.input.t9.T9ResponsivenessTrace
import org.fcitx.fcitx5.android.input.t9.T9ShortcutCandidateLayout
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
    private var showAfterPositionedContentReady = true
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
    private var t9ShownUsesLocalBudget = false
    private val t9CandidateUiSnapshotPipeline = T9CandidateUiSnapshotPipeline(
        characterBudget = { t9HanziCharacterBudget },
        widthBudget = ::t9CandidateWidthBudget,
        candidateMatchesPrefix = { candidate, prefix ->
            service.candidateMatchesT9ResolvedPrefix(candidate, prefix)
        }
    )
    private val t9CandidateInteractionController = T9CandidateInteractionController(
        pipeline = t9CandidateUiSnapshotPipeline,
        host = object : T9CandidateInteractionController.Host {
            override fun setSmartEnglishCandidateIndex(originalIndex: Int): Boolean =
                service.setSmartEnglishCandidateIndex(originalIndex)

            override fun commitSmartEnglishCandidate(originalIndex: Int): Boolean =
                service.commitSmartEnglishCandidate(originalIndex)

            override fun commitPendingPunctuationCandidate(originalIndex: Int): Boolean =
                service.commitPendingT9PunctuationCandidate(originalIndex)

            override fun previewPendingPunctuationCandidate(originalIndex: Int): Boolean =
                service.previewPendingT9PunctuationCandidate(originalIndex)

            override fun applyShownPage(shown: T9PagedCandidates) {
                t9ShownPaged = shown.data
            }

            override fun refreshT9Ui() {
                this@CandidatesView.refreshT9Ui()
            }

            override fun selectBulkCandidate(
                originalIndex: Int,
                selectedCandidate: FcitxEvent.Candidate,
                matchedPrefix: String?
            ): Boolean = selectT9BulkCandidate(originalIndex, selectedCandidate, matchedPrefix)
        }
    )
    private val t9RefreshScheduler = T9UiRefreshScheduler(
        postRefresh = { block -> postOnAnimation(block) },
        refreshNow = ::updateUi
    )
    private val t9PinyinMeasurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val t9CandidateMeasurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val t9CandidateUiStateBuilder = T9CandidateUiStateBuilder(object : T9CandidateUiStateBuilder.Pipeline {
        override fun buildSmartEnglishPaged(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates = t9CandidateUiSnapshotPipeline.buildSmartEnglishPaged(data)

        override fun buildT9PendingPunctuationPaged(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates = t9CandidateUiSnapshotPipeline.buildPendingPunctuationPaged(data)

        override fun resetT9BulkFilterState() {
            this@CandidatesView.resetT9BulkFilterState()
        }

        override fun requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active: Boolean, prefixes: List<String>) {
            this@CandidatesView.requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active, prefixes)
        }

        override fun getT9BulkFilterState(): ChineseT9CandidatePipeline.BulkFilterState =
            t9CandidateUiSnapshotPipeline.chineseBulkFilterState

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

        override fun buildT9CursorContextSignature(
            preedit: CharSequence,
            prefixes: List<String>
        ): String =
            t9CandidateUiSnapshotPipeline.buildChineseCursorContextSignature(preedit, prefixes)

        override fun applyT9HanziCursor(
            data: FcitxEvent.PagedCandidateEvent.Data,
            cursorContextSignature: String
        ): FcitxEvent.PagedCandidateEvent.Data =
            t9CandidateUiSnapshotPipeline.applyChineseHanziCursor(data, cursorContextSignature)

        override fun getT9PresentationState(
            key: ChineseT9PresentationSnapshotKey
        ): T9PresentationState = service.getT9PresentationState(key)

        override fun clearHiddenChineseT9CompositionIfCandidateUiSuppressed() {
            service.clearHiddenChineseT9CompositionIfCandidateUiSuppressed()
        }

    })
    private val showPaginationArrows by candidatesPrefs.showPaginationArrows
    private val candidatesUi = PagedCandidatesUi(
        ctx, theme, setupTextViewCandidates,
        showPaginationArrows,
        dp(windowRadius),
        dp(candidateItemSpacing),
        onCandidateClick = { shownIndex ->
            if (service.isSmartEnglishT9InputModeActive()) {
                commitSmartEnglishShortcut(shownIndex)
            } else {
                service.moveT9CandidateFocus(T9CandidateFocus.BOTTOM)
                updateT9FocusIndicator()
                commitT9HanziShortcut(shownIndex)
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
    private val t9ShortcutCandidatesUi = T9ShortcutCandidatesUi(
        ctx, theme, setupTextViewCandidates,
        showPaginationArrows,
        dp(windowRadius),
        dp(candidateItemSpacing),
        onCandidateClick = { shownIndex ->
            if (service.isSmartEnglishT9InputModeActive()) {
                commitSmartEnglishShortcut(shownIndex)
            } else {
                service.moveT9CandidateFocus(T9CandidateFocus.BOTTOM)
                updateT9FocusIndicator()
                commitT9HanziShortcut(shownIndex)
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
    private val t9PinyinRowAdapter by lazy {
        T9PinyinRowAndroidAdapter(
            barView = pinyinBarView,
            rowWrapper = pinyinRowWrapper,
            overflowHint = pinyinOverflowHint,
            chipAdapter = pinyinBarAdapter,
            window = object : T9PinyinRowAndroidAdapter.Window {
                override fun clear() {
                    t9CandidateUiSnapshotPipeline.clearPinyinWindow()
                }

                override fun submit(candidates: List<String>): T9PinyinRowWindow.VisibleState =
                    t9CandidateUiSnapshotPipeline.submitPinyinWindow(candidates)

                override fun move(delta: Int): T9PinyinRowWindow.VisibleState? =
                    t9CandidateUiSnapshotPipeline.movePinyinWindow(delta)

                override fun resetHighlight(): T9PinyinRowWindow.VisibleState? =
                    t9CandidateUiSnapshotPipeline.resetPinyinHighlight()

                override fun currentState(): T9PinyinRowWindow.VisibleState? =
                    t9CandidateUiSnapshotPipeline.currentPinyinWindowState()

                override fun highlightedPinyin(): String? =
                    t9CandidateUiSnapshotPipeline.highlightedPinyin()
            },
            delegate = object : T9PinyinRowAndroidAdapter.Delegate {
                override val rowHeightPx: Int
                    get() = pinyinBarRowHeightPx

                override val rowGapPx: Int
                    get() = pinyinToHanziGapPx

                override fun surfacePlan(
                    state: T9PinyinRowWindow.VisibleState,
                    renderedItems: List<String>,
                    candidateRowWidthPx: Int?
                ): T9PinyinRowSurfacePlanner.Plan? =
                    currentPinyinSurfacePlan(
                        candidateRowWidthPx = candidateRowWidthPx,
                        state = state,
                        renderedItems = renderedItems
                    )

                override fun viewportWidthPx(): Int? = pinyinRowViewportWidthPx()

                override fun setCandidateRowTopOffset(offset: Int) {
                    this@CandidatesView.setCandidateRowTopOffset(offset)
                }

                override fun isCandidateSurfaceWaitingForPosition(): Boolean = showAfterPositioned

                override fun showCandidateSurfaceWhenPositioned(contentReady: Boolean) {
                    showWhenPositioned(contentReady)
                }

                override fun requestCandidateSurfacePositionUpdate() {
                    shouldUpdatePosition = true
                }
            }
        )
    }
    private val candidateRowWrapper = FrameLayout(ctx).apply {
        clipChildren = false
        clipToPadding = false
        addView(candidatesUi.root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        addView(t9ShortcutCandidatesUi.root, FrameLayout.LayoutParams(
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

    private val t9CandidateSurfaceAdapter: T9CandidateSurfaceAndroidAdapter = T9CandidateSurfaceAndroidAdapter(
        preeditUi = preeditUi,
        pinyinRowAdapter = t9PinyinRowAdapter,
        candidatesUi = candidatesUi,
        shortcutCandidatesUi = t9ShortcutCandidatesUi,
        shortcutCandidateLayout = ::t9ShortcutCandidateLayout,
        setPreferAboveCursorAnchor = { preferAboveCursorAnchor = it },
        showWhenPositioned = ::showWhenPositioned,
        hideSurfaceImmediately = {
            // RecyclerView won't update its items when ancestor view is GONE.
            showAfterPositioned = false
            showAfterPositionedContentReady = true
            t9PinyinRowAdapter.removeRevealListener()
            visibility = INVISIBLE
        }
    )
    private val t9CandidateUiRenderer = T9CandidateUiRenderer(t9CandidateSurfaceAdapter)

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
                    digitSequence = service.getT9CompositionDigitSequence()
                )
                refreshT9Ui()
            }
            else -> {}
        }
    }

    fun clearTransientState() {
        t9RefreshScheduler.cancel()
        showAfterPositioned = false
        showAfterPositionedContentReady = true
        t9CandidateSurfaceAdapter.removePinyinRevealListener()
        inputPanel = FcitxEvent.InputPanelEvent.Data()
        paged = FcitxEvent.PagedCandidateEvent.Data.Empty
        resetT9BulkFilterState()
        t9CandidateUiSnapshotPipeline.reset()
        t9CandidateUiRenderer.reset()
        chineseT9CandidateLoadingState.reset()
        t9CandidateSurfaceAdapter.clear(inputPanel, paged, orientation)
        service.moveT9CandidateFocus(T9CandidateFocus.BOTTOM)
        updateT9FocusIndicator()
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
            digitSequence = service.getT9CompositionDigitSequence()
        )
        refreshT9Ui()
    }

    fun hideT9CandidateUiImmediately() {
        t9RefreshScheduler.cancel()
        showAfterPositioned = false
        showAfterPositionedContentReady = true
        t9CandidateSurfaceAdapter.removePinyinRevealListener()
        t9CandidateUiRenderer.hideImmediately()
    }

    fun getHighlightedT9Pinyin(): String? = t9PinyinRowAdapter.highlightedPinyin()

    fun commitT9HanziShortcut(index: Int): Boolean {
        t9CandidateInteractionController.commitBottomCandidate(index)?.let { return it }
        if (t9CandidateUiSnapshotPipeline.ownsCurrentShownState) return false
        return selectT9ShownHanziCandidate(index)
    }

    fun commitSmartEnglishShortcut(index: Int): Boolean {
        return t9CandidateInteractionController.commitSmartEnglishShortcut(index)
    }

    fun commitT9PendingPunctuationShortcut(index: Int): Boolean {
        return t9CandidateInteractionController.commitPendingPunctuationShortcut(index)
    }

    fun getT9PreviewCommitText(): String? {
        if (!service.isChineseT9InputModeActive()) return null
        if (service.getT9CompositionKeyCount() <= 0) return null
        val shown = t9ShownPaged ?: paged
        val snapshot = service.getChineseT9InputSnapshot(inputPanel)
        val key = snapshot.presentationKey(
            pendingPunctuationText = null,
            inputPanel = inputPanel,
            paged = shown
        )
        val preview = service.getT9PresentationState(key)
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
        return t9PinyinRowAdapter.moveHighlighted(delta)
    }

    fun hasT9BottomCandidateRow(): Boolean =
        t9CandidateUiSnapshotPipeline.hasCurrentBottomCandidateRow ||
            t9ShownPaged?.candidates?.isNotEmpty() == true

    fun moveHighlightedT9BottomCandidate(delta: Int): Boolean {
        t9CandidateInteractionController.moveBottomCandidate(delta)?.let { return it }
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
        t9CandidateInteractionController.offsetBottomCandidatePage(delta)?.let { return it }
        if (t9CandidateUiSnapshotPipeline.ownsCurrentShownState) return false
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
        t9CandidateInteractionController.commitBottomCandidate()?.let { return it }
        if (t9CandidateUiSnapshotPipeline.ownsCurrentShownState) return false
        val shown = t9ShownPaged ?: return false
        val shownIndex = shown.cursorIndex
        if (shownIndex !in shown.candidates.indices) return false
        return selectT9ShownHanziCandidate(shownIndex)
    }

    private fun updateT9FocusIndicator(
        focus: T9CandidateFocus = service.getT9CandidateFocus()
    ) {
        t9CandidateSurfaceAdapter.renderFocus(focus)
    }

    private fun updateUi() = T9ResponsivenessTrace.measure("CandidatesView.updateUi") {
        buildT9CandidateUiState()?.let { snapshot ->
            applyT9CandidateUiSnapshot(snapshot)
            t9CandidateUiRenderer.render(snapshot.renderState)
        }
    }

    private fun buildT9CandidateUiState(): T9CandidateUiSnapshot? {
        val chineseT9Active = service.isChineseT9InputModeActive()
        val smartEnglishActive = t9InputModeEnabled &&
            !chineseT9Active &&
            service.isSmartEnglishT9InputModeActive()
        // Product decision: collect volatile service/view state once per frame so the snapshot
        // pipeline decides from a stable picture instead of interleaving getters with UI rules.
        return t9CandidateUiStateBuilder.build(
            T9CandidateUiInputSnapshot(
                t9InputModeEnabled = t9InputModeEnabled,
                inputPanel = inputPanel,
                rawPaged = paged,
                orientation = orientation,
                currentlyVisible = visibility == VISIBLE,
                loadingState = chineseT9CandidateLoadingState,
                widthBudget = t9CandidateWidthBudget(),
                chineseT9Active = chineseT9Active,
                smartEnglishActive = smartEnglishActive,
                chineseSnapshot = if (chineseT9Active) {
                    service.getChineseT9InputSnapshot(inputPanel)
                } else {
                    null
                },
                smartEnglishRawPaged = if (smartEnglishActive) {
                    service.getSmartEnglishT9Paged()
                } else {
                    null
                },
                pendingPunctuationRawPaged = if (chineseT9Active || smartEnglishActive) {
                    service.getPendingT9PunctuationPaged()
                } else {
                    null
                },
                smartEnglishPresentation = if (smartEnglishActive) {
                    service.getSmartEnglishT9Presentation()
                } else {
                    null
                },
                currentFocus = service.getT9CandidateFocus()
            )
        )
    }

    private fun applyT9CandidateUiSnapshot(snapshot: T9CandidateUiSnapshot) {
        snapshot.focusCorrection?.let(service::moveT9CandidateFocus)
        applyT9ShownState(snapshot.shownState)
    }

    private fun applyT9ShownState(shownState: T9CandidateUiStateBuilder.ShownState) {
        val snapshot = t9CandidateUiSnapshotPipeline.updateShownState(
            paged = shownState.paged,
            originalIndices = shownState.originalIndices,
            usesSmartEnglish = shownState.usesSmartEnglish,
            usesPendingPunctuation = shownState.usesPendingPunctuation,
            usesBulkSelection = shownState.usesBulkSelection,
            matchedPrefix = shownState.matchedPrefix
        )
        t9ShownPaged = shownState.paged
        t9ShownOriginalIndices = if (snapshot.ownsPagingState) {
            intArrayOf()
        } else {
            shownState.originalIndices
        }
        t9ShownUsesLocalBudget = shownState.usesLocalBudget
    }

    private fun selectT9ShownHanziCandidate(shownIndex: Int): Boolean {
        val shown = t9ShownPaged ?: return false
        val originalIndex = t9ShownOriginalIndices.getOrNull(shownIndex) ?: return false
        if (originalIndex < 0) return false
        val selectedCandidate = shown.candidates.getOrNull(shownIndex) ?: return false
        val prefixToConsume = t9CandidateUiSnapshotPipeline.currentShownMatchedPrefix?.takeIf {
            service.shouldConsumeT9ResolvedPinyinPrefixAfterHanziSelection(it, selectedCandidate)
        }
        fcitx.launchOnReady {
            val selected = it.select(originalIndex)
            if (selected && prefixToConsume != null) {
                post { service.consumeT9ResolvedPinyinPrefix(prefixToConsume) }
            } else if (selected && service.isChineseT9InputModeActive()) {
                post { service.consumeT9PinyinFromSelectedCandidate(selectedCandidate) }
            }
        }
        return true
    }

    private fun offsetT9LocalBudgetedCandidatePage(delta: Int): Boolean {
        if (!t9CandidateUiSnapshotPipeline.offsetChineseLocalBudgetedPage(delta)) return false
        refreshT9Ui()
        return true
    }

    private fun selectT9BulkCandidate(
        originalIndex: Int,
        selectedCandidate: FcitxEvent.Candidate,
        matchedPrefix: String?
    ): Boolean {
        val prefixToConsume = matchedPrefix?.takeIf {
            service.shouldConsumeT9ResolvedPinyinPrefixAfterHanziSelection(it, selectedCandidate)
        }
        fcitx.launchOnReady {
            val selected = it.selectFromAll(originalIndex)
            if (selected && prefixToConsume != null) {
                post { service.consumeT9ResolvedPinyinPrefix(prefixToConsume) }
            } else if (selected && service.isChineseT9InputModeActive()) {
                post { service.consumeT9PinyinFromSelectedCandidate(selectedCandidate) }
            }
        }
        return true
    }

    private fun requestT9BulkFilteredCandidatesIfNeeded(
        t9InputModeEnabled: Boolean,
        prefixes: List<String>
    ) {
        if (!t9InputModeEnabled) {
            resetT9BulkFilterState()
            return
        }
        val signature = t9CandidateUiSnapshotPipeline.chineseBulkFilterRequestSignature(
            prefixes = prefixes,
            preedit = inputPanel.preedit.toString(),
            candidates = paged.candidates
        )
        if (!t9CandidateUiSnapshotPipeline.shouldRequestChineseBulkFilter(signature)) return
        t9CandidateUiSnapshotPipeline.startChineseBulkFilterRequest(prefixes, signature)
        val layoutHint = paged.layoutHint
        fcitx.launchOnReady { api ->
            val rawCandidates = api.getCandidates(0, T9_BULK_FILTER_LIMIT)
            post {
                t9CandidateUiSnapshotPipeline.finishChineseBulkFilterRequest(
                    signature = signature,
                    rawCandidates = rawCandidates.toList(),
                    prefixes = prefixes,
                    layoutHint = layoutHint
                )
                    ?: return@post
                refreshT9Ui()
            }
        }
    }

    private fun resetT9BulkFilterState() {
        t9CandidateUiSnapshotPipeline.resetChineseBulkFilterState()
        t9ShownOriginalIndices = intArrayOf()
        t9ShownUsesLocalBudget = false
    }

    private fun resetT9LocalBudgetState() {
        t9CandidateUiSnapshotPipeline.resetChineseLocalBudgetState()
        t9ShownUsesLocalBudget = false
    }

    private fun setCandidateRowTopOffset(offset: Int) {
        (candidateRowWrapper.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.topMargin != offset) {
                params.topMargin = offset
                candidateRowWrapper.layoutParams = params
            }
        }
    }

    private fun pinyinRowWidths(
        visiblePinyin: List<String> = t9PinyinRowAdapter.renderedItems
    ): T9PinyinRowWidthCalculator.Widths? {
        visiblePinyin.takeIf { it.isNotEmpty() }
            ?: return null
        val paint = configuredPinyinMeasurePaint()
        return T9PinyinRowWidthCalculator.calculate(
            T9PinyinRowWidthCalculator.Input(
                items = visiblePinyin,
                minVisibleChips = T9_PINYIN_ROW_MIN_VISIBLE_CHIPS,
                chipHorizontalPaddingPx = dpCandidates(itemPaddingHorizontal),
                chipSpacingPx = dpCandidates(itemPaddingHorizontal),
                overflowHintTextWidthPx = pinyinOverflowHintTextWidthPx(),
                overflowHintSpacingPx = dpCandidates(itemPaddingHorizontal),
                foldedEdgeSafetyPx = dp(T9_PINYIN_ROW_FOLDED_EDGE_SAFETY_DP),
                measureTextWidthPx = { pinyinTextWidthPx(it, paint) }
            )
        )
    }

    private fun configuredPinyinMeasurePaint(): TextPaint =
        t9PinyinMeasurePaint.apply {
            textSize = compactTopRowFontSizeSp * ctx.resources.displayMetrics.scaledDensity
            InputUiFont.applyTo(this)
        }

    private fun pinyinTextWidthPx(text: String, paint: TextPaint): Int {
        return ceil(paint.measureText(text).toDouble()).toInt()
    }

    private fun pinyinChipWidthsPx(items: List<String>): List<Int> {
        val chipPaddingPx = dpCandidates(itemPaddingHorizontal)
        val paint = configuredPinyinMeasurePaint()
        return items.map { pinyin ->
            pinyinTextWidthPx(pinyin, paint) + chipPaddingPx * 2
        }
    }

    private fun currentPinyinSurfacePlan(
        candidateRowWidthPx: Int? = null,
        state: T9PinyinRowWindow.VisibleState? = t9PinyinRowAdapter.currentWindowStateForLayout(),
        renderedItems: List<String> = state?.items ?: t9PinyinRowAdapter.renderedItems,
        widths: T9PinyinRowWidthCalculator.Widths? = pinyinRowWidths(renderedItems),
        pinyinChipWidthsPx: List<Int> = state?.items?.let(::pinyinChipWidthsPx).orEmpty()
    ): T9PinyinRowSurfacePlanner.Plan? {
        val pinyinState = state ?: return null
        if (pinyinState.items.isEmpty()) return null
        val resolvedWidths = widths ?: return null
        val overrideWidth = candidateRowWidthPx
        if (overrideWidth != null) {
            return T9PinyinRowSurfacePlanner.plan(
                T9PinyinRowSurfacePlanner.Input(
                    candidateMeasuredWidthPx = overrideWidth,
                    fallbackViewportWidthPx = pinyinRowViewportWidthPx(),
                    state = pinyinState,
                    widths = resolvedWidths,
                    chipWidthsPx = pinyinChipWidthsPx,
                    chipSpacingPx = dpCandidates(itemPaddingHorizontal),
                    maxRowWidthPx = pinyinRowMaxWidthPx(),
                    minVisibleChips = T9_PINYIN_ROW_MIN_VISIBLE_CHIPS,
                    focused = service.getT9CandidateFocus() == T9CandidateFocus.TOP
                )
            )
        }
        return t9ShownPaged
            ?.let { candidates ->
                t9CandidateSurfacePlan(
                    candidates = candidates,
                    pinyinState = pinyinState,
                    pinyinWidths = resolvedWidths,
                    pinyinChipWidthsPx = pinyinChipWidthsPx
                ).pinyinSurface
            }
    }

    private fun pinyinRowViewportWidthPx(): Int? {
        pinyinRowWrapper.width.takeIf { it > 0 }?.let { return it }
        pinyinRowWrapper.measuredWidth.takeIf { it > 0 }?.let { return it }
        (pinyinRowWrapper.layoutParams as? FrameLayout.LayoutParams)
            ?.width
            ?.takeIf { it > 0 }
            ?.let { return it }
        return null
    }

    private fun pinyinOverflowHintTextWidthPx(): Int {
        val paint = configuredPinyinMeasurePaint()
        return ceil(paint.measureText("\u2026").toDouble()).toInt()
            .coerceAtLeast(dp(T9_PINYIN_ROW_OVERFLOW_HINT_MIN_WIDTH_DP))
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

    private fun t9ShortcutCandidateLayout(
        candidates: FcitxEvent.PagedCandidateEvent.Data
    ): T9ShortcutCandidateLayout {
        return t9CandidateSurfacePlan(candidates).shortcutLayout
    }

    private fun t9CandidateSurfacePlan(
        candidates: FcitxEvent.PagedCandidateEvent.Data,
        pinyinState: T9PinyinRowWindow.VisibleState? = t9PinyinRowAdapter.currentWindowStateForLayout(),
        pinyinWidths: T9PinyinRowWidthCalculator.Widths? =
            pinyinRowWidths(pinyinState?.items ?: t9PinyinRowAdapter.renderedItems),
        pinyinChipWidthsPx: List<Int> = pinyinState?.items?.let(::pinyinChipWidthsPx).orEmpty()
    ): T9CandidateSurfacePlanner.Plan =
        T9CandidateSurfacePlanner.plan(
            T9CandidateSurfacePlanner.Input(
                candidates = candidates,
                widthBudget = t9CandidateWidthBudget(),
                rowHorizontalPaddingPx = t9ShortcutRowPaddingPx(),
                trailingPaddingPx = t9ShortcutTrailingPaddingPx(),
                showPaginationArrows = showPaginationArrows,
                paginationWidthPx = dp(T9_PAGINATION_WIDTH_DP),
                candidateVisualWidthPx = t9ShortcutCandidatesUi.measuredToolbarWidthPx,
                pinyinState = pinyinState,
                pinyinWidths = pinyinWidths,
                pinyinChipWidthsPx = pinyinChipWidthsPx,
                pinyinChipSpacingPx = dpCandidates(itemPaddingHorizontal),
                pinyinFallbackViewportWidthPx = pinyinRowViewportWidthPx(),
                maxRowWidthPx = pinyinRowMaxWidthPx(),
                minVisiblePinyinChips = T9_PINYIN_ROW_MIN_VISIBLE_CHIPS,
                pinyinRowFocused = service.getT9CandidateFocus() == T9CandidateFocus.TOP
            )
        )

    private fun t9ShortcutRowPaddingPx(): Int =
        (dp(windowRadius) * 0.35f).roundToInt().coerceAtLeast(dp(2))

    private fun t9ShortcutTrailingPaddingPx(): Int =
        dp(candidateItemSpacing)

    private fun t9CandidateWidthBudget(): T9CandidateWidthBudget {
        val maxWidthPx = pinyinRowMaxWidthPx()
        val itemSpacingPx = dp(candidateItemSpacing)
        val minimumCandidateWidthPx = (fontSize * ctx.resources.displayMetrics.scaledDensity * 1.35f)
            .roundToInt()
            .coerceAtLeast(1)
        val horizontalPaddingPx = dpCandidates(itemPaddingHorizontal)
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
        showAfterPositionedContentReady = contentReady
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
            if (!showAfterPositionedContentReady) {
                // Product decision: the first Chinese T9 pinyin-filter frame should appear as one
                // complete bubble. Positioning may finish before the pinyin row has a trustworthy
                // width, so keep the whole candidate surface invisible until that row is ready.
                shouldUpdatePosition = true
                return
            }
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
            showAfterPositionedContentReady = true
            t9CandidateSurfaceAdapter.removePinyinRevealListener()
            touchEventReceiverWindow.dismiss()
        }
        super.setVisibility(visibility)
    }

    override fun onDetachedFromWindow() {
        t9CandidateSurfaceAdapter.removePinyinRevealListener()
        viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        touchEventReceiverWindow.dismiss()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val T9_BULK_FILTER_LIMIT = 80
        private const val T9_PINYIN_TO_HANZI_GAP_DP = 2
        private const val T9_PINYIN_ROW_MIN_VISIBLE_CHIPS = 4
        private const val T9_PINYIN_ROW_OVERFLOW_HINT_MIN_WIDTH_DP = 10
        private const val T9_PINYIN_ROW_FOLDED_EDGE_SAFETY_DP = 2
        private const val T9_PAGINATION_WIDTH_DP = 20
    }
}

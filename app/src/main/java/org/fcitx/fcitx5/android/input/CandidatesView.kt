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
import org.fcitx.fcitx5.android.input.t9.ChineseT9InputSnapshot
import org.fcitx.fcitx5.android.input.t9.T9CandidateBudget
import org.fcitx.fcitx5.android.input.t9.T9CandidateFocus
import org.fcitx.fcitx5.android.input.t9.T9CandidateInteractionController
import org.fcitx.fcitx5.android.input.t9.T9CandidateSurfaceAndroidAdapter
import org.fcitx.fcitx5.android.input.t9.T9CandidateSurfaceGeometry
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiInputSnapshot
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiSnapshot
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiSnapshotPipeline
import org.fcitx.fcitx5.android.input.t9.T9CandidateUiRenderer
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowAndroidAdapter
import org.fcitx.fcitx5.android.input.t9.T9PinyinChipAdapter
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowSurfacePlanner
import org.fcitx.fcitx5.android.input.t9.T9PinyinRowWindow
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

    private val chineseT9CandidateLoadingState = ChineseT9CandidateLoadingState()

    /**
     * layout update may or may not cause [CandidatesView]'s size [onSizeChanged],
     * in either case, we should reposition it
     */
    private val layoutListener = OnGlobalLayoutListener {
        floatingWindowController.requestPositionUpdate()
    }

    /**
     * [CandidatesView]'s position is calculated based on it's size,
     * so we need to recalculate the position after layout,
     * and before any actual drawing to avoid flicker
     */
    private val preDrawListener = OnPreDrawListener {
        floatingWindowController.onPreDraw(floatingWindowPositionConfig())
        true
    }

    private val touchEventReceiverWindow = TouchEventReceiverWindow(this)
    private val floatingWindowController = FloatingCandidateWindowController(
        object : FloatingCandidateWindowController.Host {
            override val visibility: Int
                get() = this@CandidatesView.visibility

            override val width: Int
                get() = this@CandidatesView.width

            override val height: Int
                get() = this@CandidatesView.height

            override val minWidth: Int
                get() = this@CandidatesView.minWidth

            override var maxWidth: Int
                get() = this@CandidatesView.maxWidth
                set(value) {
                    this@CandidatesView.maxWidth = value
                }

            override fun requestLayout() {
                this@CandidatesView.requestLayout()
            }

            override fun invalidate() {
                this@CandidatesView.invalidate()
            }

            override fun setTranslation(x: Float, y: Float) {
                this@CandidatesView.translationX = x
                this@CandidatesView.translationY = y
            }

            override fun setVisibilityImmediately(visibility: Int) {
                setCandidateWindowVisibilityImmediately(visibility)
            }

            override fun showTouchReceiverAt(x: Int, y: Int, width: Int, height: Int) {
                touchEventReceiverWindow.showAt(x, y, width, height)
            }

            override fun dismissTouchReceiver() {
                touchEventReceiverWindow.dismiss()
            }
        }
    )

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

    private val t9CandidateUiSnapshotPipeline = T9CandidateUiSnapshotPipeline(
        characterBudget = { t9HanziCharacterBudget },
        widthBudget = ::t9CandidateWidthBudget,
        candidateMatchesPrefix = { candidate, prefix ->
            service.candidateMatchesT9ResolvedPrefix(candidate, prefix)
        },
        requestBulkCandidates = ::requestT9BulkFilteredCandidatesIfNeeded,
        getPresentationState = service::getT9PresentationState,
        clearHiddenComposition = service::clearHiddenChineseT9CompositionIfCandidateUiSuppressed
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

            override fun refreshT9Ui() {
                this@CandidatesView.refreshT9Ui()
            }

            override fun offsetEngineCandidatePage(delta: Int): Boolean {
                fcitx.launchOnReady { it.offsetCandidatePage(delta) }
                return true
            }

            override fun selectChineseCandidate(
                originalIndex: Int,
                selectedCandidate: FcitxEvent.Candidate,
                matchedPrefix: String?,
                fromAllCandidates: Boolean
            ): Boolean = selectT9ChineseCandidate(
                originalIndex,
                selectedCandidate,
                matchedPrefix,
                fromAllCandidates
            )
        }
    )
    private val t9RefreshScheduler = T9UiRefreshScheduler(
        postRefresh = { block -> postOnAnimation(block) },
        refreshNow = ::updateUi
    )
    private val t9PinyinMeasurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val t9CandidateMeasurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val t9CandidateSurfaceGeometry = T9CandidateSurfaceGeometry(
        measurePinyinTextWidthPx = ::measureT9PinyinTextWidthPx,
        measureCandidateTextWidthPx = ::measureT9CandidateTextWidthPx
    )
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

                override fun isCandidateSurfaceWaitingForPosition(): Boolean =
                    floatingWindowController.isWaitingForPosition

                override fun showCandidateSurfaceWhenPositioned(contentReady: Boolean) {
                    floatingWindowController.showWhenPositioned(contentReady)
                }

                override fun requestCandidateSurfacePositionUpdate() {
                    floatingWindowController.requestPositionUpdate()
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
        setPreferAboveCursorAnchor = floatingWindowController::setPreferAboveCursorAnchor,
        showWhenPositioned = floatingWindowController::showWhenPositioned,
        hideSurfaceImmediately = {
            // RecyclerView won't update its items when ancestor view is GONE.
            floatingWindowController.onSurfaceHidden()
            t9PinyinRowAdapter.removeRevealListener()
            setCandidateWindowVisibilityImmediately(INVISIBLE)
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
        floatingWindowController.onSurfaceHidden()
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
        floatingWindowController.onSurfaceHidden()
        t9CandidateSurfaceAdapter.removePinyinRevealListener()
        t9CandidateUiRenderer.hideImmediately()
    }

    fun getHighlightedT9Pinyin(): String? = t9PinyinRowAdapter.highlightedPinyin()

    fun commitT9HanziShortcut(index: Int): Boolean {
        return t9CandidateInteractionController.commitBottomCandidate(index) ?: false
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
        val shown = t9CandidateUiSnapshotPipeline.currentShownSnapshot?.paged ?: paged
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
        t9CandidateUiSnapshotPipeline.hasCurrentBottomCandidateRow

    fun moveHighlightedT9BottomCandidate(delta: Int): Boolean {
        return t9CandidateInteractionController.moveBottomCandidate(delta) ?: false
    }

    fun offsetT9BottomCandidatePage(delta: Int): Boolean {
        return t9CandidateInteractionController.offsetBottomCandidatePage(delta) ?: false
    }

    fun commitHighlightedT9BottomCandidate(): Boolean {
        return t9CandidateInteractionController.commitBottomCandidate() ?: false
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
        return t9CandidateUiSnapshotPipeline.build(
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
    }

    private fun selectT9ChineseCandidate(
        originalIndex: Int,
        selectedCandidate: FcitxEvent.Candidate,
        matchedPrefix: String?,
        fromAllCandidates: Boolean
    ): Boolean {
        val prefixToConsume = matchedPrefix?.takeIf {
            service.shouldConsumeT9ResolvedPinyinPrefixAfterHanziSelection(it, selectedCandidate)
        }
        fcitx.launchOnReady {
            val selected = if (fromAllCandidates) {
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
    }

    private fun setCandidateRowTopOffset(offset: Int) {
        (candidateRowWrapper.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.topMargin != offset) {
                params.topMargin = offset
                candidateRowWrapper.layoutParams = params
            }
        }
    }

    private fun measureT9PinyinTextWidthPx(text: String): Int {
        val paint = t9PinyinMeasurePaint.apply {
            textSize = compactTopRowFontSizeSp * ctx.resources.displayMetrics.scaledDensity
            InputUiFont.applyTo(this)
        }
        return ceil(paint.measureText(text).toDouble()).toInt()
    }

    private fun measureT9CandidateTextWidthPx(text: String): Int {
        val paint = t9CandidateMeasurePaint.apply {
            textSize = fontSize * ctx.resources.displayMetrics.scaledDensity
            InputUiFont.applyTo(this)
        }
        return paint.measureText(text).roundToInt()
    }

    private fun currentPinyinSurfacePlan(
        candidateRowWidthPx: Int? = null,
        state: T9PinyinRowWindow.VisibleState? = t9PinyinRowAdapter.currentWindowStateForLayout(),
        renderedItems: List<String> = state?.items ?: t9PinyinRowAdapter.renderedItems
    ): T9PinyinRowSurfacePlanner.Plan? {
        val candidates = t9CandidateUiSnapshotPipeline.currentShownSnapshot?.paged
            ?: candidateRowWidthPx?.let { FcitxEvent.PagedCandidateEvent.Data.Empty }
            ?: return null
        return t9CandidateSurfaceGeometry.pinyinSurfacePlan(
            input = t9CandidateSurfaceGeometryInput(
                candidates = candidates,
                pinyinState = state,
                renderedPinyinItems = renderedItems
            ),
            candidateRowWidthPx = candidateRowWidthPx
        )
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
        return measureT9PinyinTextWidthPx("\u2026")
            .coerceAtLeast(dp(T9_PINYIN_ROW_OVERFLOW_HINT_MIN_WIDTH_DP))
    }

    private fun pinyinRowMaxWidthPx(): Int {
        val parentWidthPx = floatingWindowController.parentWidthPx
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
        candidates: FcitxEvent.PagedCandidateEvent.Data
    ) =
        t9CandidateSurfaceGeometry.surfacePlan(
            t9CandidateSurfaceGeometryInput(candidates = candidates)
        )

    private fun t9CandidateSurfaceGeometryInput(
        candidates: FcitxEvent.PagedCandidateEvent.Data,
        pinyinState: T9PinyinRowWindow.VisibleState? = t9PinyinRowAdapter.currentWindowStateForLayout(),
        renderedPinyinItems: List<String> = pinyinState?.items ?: t9PinyinRowAdapter.renderedItems
    ): T9CandidateSurfaceGeometry.SurfaceInput =
        T9CandidateSurfaceGeometry.SurfaceInput(
            candidates = candidates,
            metrics = t9CandidateSurfaceGeometryMetrics(),
            candidateVisualWidthPx = t9ShortcutCandidatesUi.measuredToolbarWidthPx,
            pinyinState = pinyinState,
            renderedPinyinItems = renderedPinyinItems,
            pinyinFallbackViewportWidthPx = pinyinRowViewportWidthPx(),
            pinyinRowFocused = service.getT9CandidateFocus() == T9CandidateFocus.TOP
        )

    private fun t9CandidateSurfaceGeometryMetrics(): T9CandidateSurfaceGeometry.Metrics =
        T9CandidateSurfaceGeometry.Metrics(
            maxRowWidthPx = pinyinRowMaxWidthPx(),
            candidateSpacingPx = dp(candidateItemSpacing),
            candidateHorizontalPaddingPx = dpCandidates(itemPaddingHorizontal),
            minimumCandidateWidthPx = (fontSize * ctx.resources.displayMetrics.scaledDensity * 1.35f)
                .roundToInt()
                .coerceAtLeast(1),
            rowHorizontalPaddingPx = t9ShortcutRowPaddingPx(),
            trailingPaddingPx = t9ShortcutTrailingPaddingPx(),
            showPaginationArrows = showPaginationArrows,
            paginationWidthPx = dp(T9_PAGINATION_WIDTH_DP),
            pinyinChipHorizontalPaddingPx = dpCandidates(itemPaddingHorizontal),
            pinyinChipSpacingPx = dpCandidates(itemPaddingHorizontal),
            pinyinOverflowHintTextWidthPx = pinyinOverflowHintTextWidthPx(),
            pinyinOverflowHintSpacingPx = dpCandidates(itemPaddingHorizontal),
            pinyinFoldedEdgeSafetyPx = dp(T9_PINYIN_ROW_FOLDED_EDGE_SAFETY_DP),
            minVisiblePinyinChips = T9_PINYIN_ROW_MIN_VISIBLE_CHIPS
        )

    private fun t9ShortcutRowPaddingPx(): Int =
        (dp(windowRadius) * 0.35f).roundToInt().coerceAtLeast(dp(2))

    private fun t9ShortcutTrailingPaddingPx(): Int =
        dp(candidateItemSpacing)

    private fun t9CandidateWidthBudget() =
        t9CandidateSurfaceGeometry.widthBudget(t9CandidateSurfaceGeometryMetrics())

    /** Horizontal gap from screen edge so the bubble doesn't touch left/right. */
    private val horizontalMarginPx: Int get() = dp(horizontalMargin)

    /** Extra bounds reserved for elevation shadows outside the visible bubble surface. */
    private val candidateShadowOutsetPx: Int get() = dp(12)

    private fun floatingWindowPositionConfig(): FloatingCandidateWindowController.PositionConfig =
        FloatingCandidateWindowController.PositionConfig(
            horizontalMarginPx = horizontalMarginPx,
            shadowOutsetPx = candidateShadowOutsetPx
        )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Enforce maxWidth so total width never exceeds (parent - 2*margin); ViewGroup does not do this by default
        val maxW = if (maxWidth > 0) maxWidth else Int.MAX_VALUE
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val constrainedSize = if (mode == MeasureSpec.UNSPECIFIED) maxW else min(size, maxW)
        val constrainedSpec = MeasureSpec.makeMeasureSpec(constrainedSize, if (mode == MeasureSpec.UNSPECIFIED) MeasureSpec.AT_MOST else mode)
        super.onMeasure(constrainedSpec, heightMeasureSpec)
    }

    private fun setCandidateWindowVisibilityImmediately(visibility: Int) {
        super.setVisibility(visibility)
    }

    fun updateCursorAnchor(@Size(4) anchor: FloatArray, @Size(2) parent: FloatArray) {
        floatingWindowController.updateCursorAnchor(anchor, parent, floatingWindowPositionConfig())
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
            floatingWindowController.setBottomInsets(getNavBarBottomInset(insets))
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
            floatingWindowController.onSurfaceHidden()
            t9CandidateSurfaceAdapter.removePinyinRevealListener()
        }
        super.setVisibility(visibility)
    }

    override fun onDetachedFromWindow() {
        t9CandidateSurfaceAdapter.removePinyinRevealListener()
        viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        floatingWindowController.onDetached()
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

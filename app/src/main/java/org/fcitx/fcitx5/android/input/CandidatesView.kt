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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Size
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.floating.PagedCandidatesUi
import org.fcitx.fcitx5.android.input.preedit.PreeditUi
import org.fcitx.fcitx5.android.input.t9.T9PinyinChipAdapter
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
import android.view.View.MeasureSpec
import kotlin.math.min
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
    private val horizontalMargin by candidatesPrefs.horizontalMargin
    private val bubbleGap by candidatesPrefs.bubbleGap
    private val candidateItemSpacing by candidatesPrefs.candidateItemSpacing
    private val smallRowHeightPercent by candidatesPrefs.smallRowHeightPercent

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

    /** Third row (candidates) use candidate font size setting as-is. */
    private val setupTextViewCandidates: TextView.() -> Unit = {
        textSize = fontSize.toFloat().coerceAtLeast(1f)
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

    /**
     * Last candidate list actually shown to the user. When T9 pinyin filtering is active this
     * differs from [paged] (the raw fcitx page) and tap indices must be translated back before
     * being sent to fcitx, otherwise taps on filtered rows would pick the wrong character.
     */
    private var t9ShownPaged: FcitxEvent.PagedCandidateEvent.Data? = null
    private var t9ShownCandidateSignature = ""
    private var t9HanziCursorIndex = -1
    private var t9ShownOriginalIndices = intArrayOf()
    private var t9ShownUsesBulkSelection = false
    private var t9BulkFilterRequestSignature = ""
    private var t9BulkFilteredPaged: FcitxEvent.PagedCandidateEvent.Data? = null
    private var t9BulkFilteredOriginalIndices = intArrayOf()

    private val showPaginationArrows by candidatesPrefs.showPaginationArrows
    private val candidatesUi = PagedCandidatesUi(
        ctx, theme, setupTextViewCandidates,
        showPaginationArrows,
        dp(windowRadius),
        onCandidateClick = { shownIndex ->
            service.moveT9CandidateFocus(FcitxInputMethodService.T9CandidateFocus.BOTTOM)
            updateT9FocusIndicator()
            selectT9ShownHanziCandidate(shownIndex)
        },
        onPrevPage = { fcitx.launchOnReady { it.offsetCandidatePage(-1) } },
        onNextPage = { fcitx.launchOnReady { it.offsetCandidatePage(1) } }
    )

    /** T9 pinyin selection bar: row above candidates, replaces number row when visible. */
    private val pinyinBarAdapter by lazy {
        T9PinyinChipAdapter(
            theme = theme,
            textSizeSp = smallRowFontSizeSp,
            horizontalPaddingPx = dpCandidates(itemPaddingHorizontal),
            verticalPaddingPx = dpCandidates(itemPaddingVertical),
            rowHeightPx = pinyinBarRowHeightPx,
            cornerRadiusPx = dpCandidates(windowRadius).toFloat(),
            onChipClick = {
                service.commitT9PinyinSelection(it)
                updateT9FocusIndicator()
            }
        )
    }
    private val pinyinBarLayoutManager = LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false)
    private val pinyinBarRecyclerView = RecyclerView(ctx).apply {
        layoutManager = pinyinBarLayoutManager
        adapter = pinyinBarAdapter
        overScrollMode = View.OVER_SCROLL_NEVER
        itemAnimator = null
        isHorizontalScrollBarEnabled = false
        visibility = View.GONE
    }
    private val pinyinRowWrapper = FrameLayout(ctx).apply {
        addView(pinyinBarRecyclerView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setOnClickListener {
            service.moveT9CandidateFocus(FcitxInputMethodService.T9CandidateFocus.TOP)
            updateT9FocusIndicator()
        }
        visibility = View.GONE
    }
    private val candidateRowWrapper = FrameLayout(ctx).apply {
        addView(candidatesUi.root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        setOnClickListener {
            service.moveT9CandidateFocus(FcitxInputMethodService.T9CandidateFocus.BOTTOM)
            updateT9FocusIndicator()
        }
    }

    private fun dpCandidates(value: Int): Int = (value * ctx.resources.displayMetrics.density).toInt()

    /** Height of one candidate row (one line), used as the "11" in 6:6:11. */
    private val oneCandidateRowHeightPx: Int
        get() {
            val dm = ctx.resources.displayMetrics
            val linePx = (fontSize * dm.scaledDensity * 1.2f).toInt()
            return linePx + 2 * dpCandidates(itemPaddingVertical)
        }

    /** First row: height = candidate row × smallRowHeightPercent%. */
    private val preeditRowHeightPx: Int get() = oneCandidateRowHeightPx * smallRowHeightPercent / 100
    /** Second row: same as first. */
    private val pinyinBarRowHeightPx: Int get() = oneCandidateRowHeightPx * smallRowHeightPercent / 100

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

    fun clearTransientState() {
        inputPanel = FcitxEvent.InputPanelEvent.Data()
        paged = FcitxEvent.PagedCandidateEvent.Data.Empty
        resetT9BulkFilterState()
        preeditUi.update(inputPanel)
        preeditUi.root.visibility = GONE
        pinyinBarAdapter.clear()
        pinyinBarRecyclerView.scrollToPosition(0)
        pinyinBarRecyclerView.visibility = View.GONE
        pinyinRowWrapper.visibility = View.GONE
        service.moveT9CandidateFocus(FcitxInputMethodService.T9CandidateFocus.BOTTOM)
        updateT9FocusIndicator()
        candidatesUi.update(paged, orientation)
        visibility = INVISIBLE
    }

    fun syncT9CandidateFocus() {
        updateT9FocusIndicator()
    }

    /**
     * Force a full UI refresh. Needed after model-only mutations (pinyin chip selection,
     * delete-reopen) that don't trigger a fcitx event and therefore wouldn't otherwise redraw.
     */
    fun refreshT9Ui() {
        updateUi()
    }

    fun getHighlightedT9Pinyin(): String? = pinyinBarAdapter.getHighlightedPinyin()

    fun moveHighlightedT9Pinyin(delta: Int): Boolean {
        val moved = pinyinBarAdapter.moveHighlightedIndex(delta)
        if (moved) {
            pinyinBarRecyclerView.scrollToPosition(pinyinBarAdapter.highlightedIndex)
        }
        return moved
    }

    fun moveHighlightedT9HanziCandidate(delta: Int): Boolean {
        val shown = t9ShownPaged ?: return false
        if (shown.candidates.isEmpty()) return false
        val next = shown.cursorIndex + delta
        return when {
            next in shown.candidates.indices -> {
                t9HanziCursorIndex = next
                val updated = shown.copy(cursorIndex = next)
                t9ShownPaged = updated
                candidatesUi.update(updated, orientation)
                true
            }
            next >= shown.candidates.size && shown.hasNext -> {
                offsetT9HanziCandidatePage(1)
            }
            next < 0 && shown.hasPrev -> {
                offsetT9HanziCandidatePage(-1)
            }
            else -> false
        }
    }

    fun offsetT9HanziCandidatePage(delta: Int): Boolean {
        val shown = t9ShownPaged ?: return false
        val canOffset = if (delta > 0) shown.hasNext else shown.hasPrev
        if (!canOffset) return false
        fcitx.launchOnReady { it.offsetCandidatePage(delta) }
        return true
    }

    fun commitHighlightedT9HanziCandidate(): Boolean {
        val shown = t9ShownPaged ?: return false
        val shownIndex = shown.cursorIndex
        if (shownIndex !in shown.candidates.indices) return false
        return selectT9ShownHanziCandidate(shownIndex)
    }

    private fun evaluateVisibility(topReading: FormattedText?, pinyinRowVisible: Boolean): Boolean {
        return inputPanel.preedit.isNotEmpty() ||
                paged.candidates.isNotEmpty() ||
                inputPanel.auxUp.isNotEmpty() ||
                inputPanel.auxDown.isNotEmpty() ||
                topReading?.isNotEmpty() == true ||
                pinyinRowVisible
    }

    private fun updateT9FocusIndicator() {
        val topFocused = service.getT9CandidateFocus() == FcitxInputMethodService.T9CandidateFocus.TOP
        pinyinBarAdapter.setHighlightActive(topFocused)
        candidatesUi.setHighlightActive(!topFocused)
    }

    private fun updateUi() {
        val t9InputModeEnabled = AppPrefs.getInstance().keyboard.useT9KeyboardLayout.getValue()
        if (t9InputModeEnabled) {
            service.syncT9CompositionWithInputPanel(inputPanel)
        }
        requestT9BulkFilteredCandidatesIfNeeded(t9InputModeEnabled)
        val filteredPaged = if (t9InputModeEnabled) service.filterPagedByResolvedPinyin(paged) else paged
        val useBulkFiltered = t9InputModeEnabled && t9BulkFilteredPaged != null
        val candidateSource = t9BulkFilteredPaged ?: filteredPaged
        val effectivePaged = if (t9InputModeEnabled) applyT9HanziCursor(candidateSource) else paged
        t9ShownPaged = effectivePaged
        t9ShownUsesBulkSelection = useBulkFiltered
        t9ShownOriginalIndices = if (useBulkFiltered) {
            t9BulkFilteredOriginalIndices
        } else {
            buildOriginalIndicesForPaged(effectivePaged)
        }
        val t9State = if (t9InputModeEnabled) {
            service.getT9PresentationState(inputPanel, effectivePaged)
        } else {
            null
        }
        val panelToShow = t9State?.topReading?.let {
            FcitxEvent.InputPanelEvent.Data(it, inputPanel.auxUp, inputPanel.auxDown)
        } ?: inputPanel
        preeditUi.update(panelToShow)
        preeditUi.root.visibility = if (preeditUi.visible) VISIBLE else GONE
        candidatesUi.update(effectivePaged, orientation)
        updatePinyinBar(t9State?.pinyinOptions ?: emptyList(), t9InputModeEnabled)
        updateT9FocusIndicator()
        if (evaluateVisibility(t9State?.topReading, t9State?.pinyinRowVisible == true)) {
            visibility = VISIBLE
        } else {
            // RecyclerView won't update its items when ancestor view is GONE
            visibility = INVISIBLE
        }
    }

    private fun applyT9HanziCursor(
        data: FcitxEvent.PagedCandidateEvent.Data
    ): FcitxEvent.PagedCandidateEvent.Data {
        val signature = buildString {
            data.candidates.forEach {
                append(it.label).append('|').append(it.text).append('|').append(it.comment).append('\n')
            }
            append(data.hasPrev).append('|').append(data.hasNext)
        }
        if (signature != t9ShownCandidateSignature) {
            t9ShownCandidateSignature = signature
            t9HanziCursorIndex = data.cursorIndex.takeIf { it in data.candidates.indices }
                ?: data.candidates.indices.firstOrNull()
                ?: -1
        } else if (t9HanziCursorIndex !in data.candidates.indices) {
            t9HanziCursorIndex = data.candidates.indices.firstOrNull() ?: -1
        }
        return if (data.cursorIndex == t9HanziCursorIndex) data else data.copy(cursorIndex = t9HanziCursorIndex)
    }

    private fun selectT9ShownHanziCandidate(shownIndex: Int): Boolean {
        val shown = t9ShownPaged ?: return false
        val originalIndex = originalCandidateIndexForShown(shown, shownIndex) ?: return false
        if (originalIndex < 0) return false
        fcitx.launchOnReady {
            if (t9ShownUsesBulkSelection) {
                it.selectFromAll(originalIndex)
            } else {
                it.select(originalIndex)
            }
        }
        return true
    }

    private fun originalCandidateIndexForShown(
        shown: FcitxEvent.PagedCandidateEvent.Data,
        shownIndex: Int
    ): Int? {
        t9ShownOriginalIndices.getOrNull(shownIndex)?.takeIf { it >= 0 }?.let { return it }
        val target = shown.candidates.getOrNull(shownIndex) ?: return null
        if (shown.candidates.contentEquals(paged.candidates)) return shownIndex
        val sameCandidateBeforeTarget = shown.candidates
            .take(shownIndex)
            .count { it == target }
        var seen = 0
        paged.candidates.forEachIndexed { rawIndex, rawCandidate ->
            if (rawCandidate == target) {
                if (seen == sameCandidateBeforeTarget) return rawIndex
                seen += 1
            }
        }
        return null
    }

    private fun buildOriginalIndicesForPaged(
        shown: FcitxEvent.PagedCandidateEvent.Data
    ): IntArray {
        if (shown.candidates.isEmpty()) return intArrayOf()
        if (shown.candidates.contentEquals(paged.candidates)) {
            return IntArray(shown.candidates.size) { it }
        }
        val seenByCandidate = mutableMapOf<FcitxEvent.Candidate, Int>()
        return IntArray(shown.candidates.size) { shownIndex ->
            val target = shown.candidates[shownIndex]
            val targetSeen = seenByCandidate.getOrDefault(target, 0)
            seenByCandidate[target] = targetSeen + 1
            var seen = 0
            paged.candidates.forEachIndexed { rawIndex, rawCandidate ->
                if (rawCandidate == target) {
                    if (seen == targetSeen) return@IntArray rawIndex
                    seen += 1
                }
            }
            -1
        }
    }

    private fun requestT9BulkFilteredCandidatesIfNeeded(t9InputModeEnabled: Boolean) {
        if (!t9InputModeEnabled) {
            resetT9BulkFilterState()
            return
        }
        val expected = service.getT9ResolvedPinyinPrefix()
        if (expected == null) {
            resetT9BulkFilterState()
            return
        }
        val signature = buildString {
            append(expected).append('|')
            append(inputPanel.preedit).append('|')
            append(paged.candidates.joinToString(separator = "\n") { "${it.text}|${it.comment}" })
        }
        if (signature == t9BulkFilterRequestSignature) return
        t9BulkFilterRequestSignature = signature
        t9BulkFilteredPaged = null
        t9BulkFilteredOriginalIndices = intArrayOf()
        fcitx.launchOnReady { api ->
            val rawCandidates = api.getCandidates(0, T9_BULK_FILTER_LIMIT)
            val filtered = rawCandidates.mapIndexedNotNull { index, raw ->
                parseBulkCandidate(raw)?.takeIf {
                    service.candidateMatchesT9ResolvedPrefix(it, expected)
                }?.let { index to it }
            }
            post {
                if (signature != t9BulkFilterRequestSignature) return@post
                t9BulkFilteredOriginalIndices = filtered.map { it.first }.toIntArray()
                t9BulkFilteredPaged = if (filtered.isEmpty()) {
                    null
                } else {
                    FcitxEvent.PagedCandidateEvent.Data(
                        candidates = filtered.map { it.second }.toTypedArray(),
                        cursorIndex = 0,
                        layoutHint = paged.layoutHint,
                        hasPrev = false,
                        hasNext = rawCandidates.size >= T9_BULK_FILTER_LIMIT
                    )
                }
                refreshT9Ui()
            }
        }
    }

    private fun resetT9BulkFilterState() {
        t9BulkFilterRequestSignature = ""
        t9BulkFilteredPaged = null
        t9BulkFilteredOriginalIndices = intArrayOf()
        t9ShownOriginalIndices = intArrayOf()
        t9ShownUsesBulkSelection = false
    }

    private fun parseBulkCandidate(raw: String): FcitxEvent.Candidate? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val splitAt = trimmed.indexOf(' ')
        return if (splitAt < 0) {
            FcitxEvent.Candidate("", trimmed, "")
        } else {
            FcitxEvent.Candidate(
                label = "",
                text = trimmed.substring(0, splitAt),
                comment = trimmed.substring(splitAt + 1).trim()
            )
        }
    }

    private fun updatePinyinBar(candidates: List<String>, useT9: Boolean) {
        if (!useT9) {
            pinyinBarAdapter.clear()
            pinyinBarRecyclerView.scrollToPosition(0)
            pinyinBarRecyclerView.visibility = View.GONE
            pinyinRowWrapper.visibility = View.GONE
            service.moveT9CandidateFocus(FcitxInputMethodService.T9CandidateFocus.BOTTOM)
            return
        }
        if (candidates.isEmpty()) {
            pinyinBarAdapter.clear()
            pinyinBarRecyclerView.scrollToPosition(0)
            pinyinBarRecyclerView.visibility = View.GONE
            pinyinRowWrapper.visibility = View.GONE
            service.moveT9CandidateFocus(FcitxInputMethodService.T9CandidateFocus.BOTTOM)
            return
        }
        val changed = pinyinBarAdapter.submitList(candidates)
        pinyinBarRecyclerView.visibility = View.VISIBLE
        pinyinRowWrapper.visibility = View.VISIBLE
        if (changed) {
            pinyinBarRecyclerView.scrollToPosition(0)
        }
    }

    private var bottomInsets = 0

    /** Horizontal gap from screen edge so the bubble doesn't touch left/right. */
    private val horizontalMarginPx: Int get() = dp(horizontalMargin)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Enforce maxWidth so total width never exceeds (parent - 2*margin); ViewGroup does not do this by default
        val maxW = if (maxWidth > 0) maxWidth else Int.MAX_VALUE
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val constrainedSize = if (mode == MeasureSpec.UNSPECIFIED) maxW else min(size, maxW)
        val constrainedSpec = MeasureSpec.makeMeasureSpec(constrainedSize, if (mode == MeasureSpec.UNSPECIFIED) MeasureSpec.AT_MOST else mode)
        super.onMeasure(constrainedSpec, heightMeasureSpec)
    }

    private fun updatePosition() {
        val (parentWidth, parentHeight) = parentSize
        val marginPx = horizontalMarginPx
        if (parentWidth > 0) {
            val maxW = (parentWidth - 2 * marginPx).toInt().coerceAtLeast(minWidth)
            if (maxWidth != maxW) {
                maxWidth = maxW
                requestLayout()
            }
        }
        if (visibility != VISIBLE) {
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
        val tX: Float = marginPx.toFloat()
        val bottomLimit = parentHeight - bottomInsets
        val bottomSpace = bottomLimit - bottom
        // move CandidatesView above cursor anchor, only when
        val tY: Float = if (
            bottom + selfHeight > bottomLimit   // bottom space is not enough
            && top > bottomSpace                // top space is larger than bottom
        ) top - selfHeight else bottom
        translationX = tX
        translationY = tY
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

        // Pinyin bar: width 0 so bubble2 width is driven only by candidates; we sync width after layout
        bubble2Wrapper.addView(pinyinRowWrapper, LinearLayout.LayoutParams(
            0,
            pinyinBarRowHeightPx
        ).apply { gravity = Gravity.START or Gravity.TOP })
        bubble2Wrapper.addView(candidateRowWrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.START or Gravity.TOP })
        (candidatesUi.root as? RecyclerView)?.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                outRect.right = dp(candidateItemSpacing)
            }
        })
        pinyinBarRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                if (parent.getChildAdapterPosition(view) != RecyclerView.NO_POSITION) {
                    outRect.right = dp(itemPaddingHorizontal)
                }
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

        // After layout, give pinyin bar the same width as candidates so it scrolls inside bubble2
        bubble2Wrapper.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val cw = candidatesUi.root.width
                if (cw > 0 && (pinyinRowWrapper.layoutParams as? LinearLayout.LayoutParams)?.width != cw) {
                    (pinyinRowWrapper.layoutParams as? LinearLayout.LayoutParams)?.width = cw
                    pinyinRowWrapper.requestLayout()
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
    }
}

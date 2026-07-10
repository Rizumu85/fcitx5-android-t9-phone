/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent

class T9CandidateSurfaceGeometry(
    private val measurePinyinTextWidthPx: (String) -> Int,
    private val measureCandidateTextWidthPx: (String) -> Int
) {
    private data class PinyinRowWidthsCacheKey(
        val items: List<String>,
        val minVisibleChips: Int,
        val chipHorizontalPaddingPx: Int,
        val chipSpacingPx: Int,
        val overflowHintTextWidthPx: Int,
        val overflowHintSpacingPx: Int,
        val foldedEdgeSafetyPx: Int
    )

    private data class PinyinChipWidthsCacheKey(
        val items: List<String>,
        val chipHorizontalPaddingPx: Int
    )

    data class Metrics(
        val maxRowWidthPx: Int,
        val candidateSpacingPx: Int,
        val candidateHorizontalPaddingPx: Int,
        val minimumCandidateWidthPx: Int,
        val rowHorizontalPaddingPx: Int,
        val trailingPaddingPx: Int,
        val showPaginationArrows: Boolean,
        val paginationWidthPx: Int,
        val pinyinChipHorizontalPaddingPx: Int,
        val pinyinChipSpacingPx: Int,
        val pinyinOverflowHintTextWidthPx: Int,
        val pinyinOverflowHintSpacingPx: Int,
        val pinyinFoldedEdgeSafetyPx: Int,
        val minVisiblePinyinChips: Int
    )

    data class SurfaceInput(
        val candidates: FcitxEvent.PagedCandidateEvent.Data,
        val metrics: Metrics,
        val pinyinState: T9PinyinRowWindow.VisibleState?,
        val renderedPinyinItems: List<String>,
        val pinyinFallbackViewportWidthPx: Int?,
        val pinyinRowFocused: Boolean
    )

    private var activeGenerationId = 0L
    private var candidateVisualWidthPx: Int? = null
    private val pinyinTextWidthCache = linkedMapOf<String, Int>()
    private var pinyinRowWidthsCacheKey: PinyinRowWidthsCacheKey? = null
    private var pinyinRowWidthsCacheValue: T9PinyinRowWidthCalculator.Widths? = null
    private var pinyinChipWidthsCacheKey: PinyinChipWidthsCacheKey? = null
    private var pinyinChipWidthsCacheValue: List<Int> = emptyList()

    fun beginFrame(generationId: Long) {
        activeGenerationId = generationId
    }

    fun observeCandidateVisualWidth(generationId: Long, widthPx: Int?) {
        if (generationId != activeGenerationId) return
        val measured = widthPx?.takeIf { it > 0 }
        if (candidateVisualWidthPx == measured) return
        candidateVisualWidthPx = measured
    }

    fun widthBudget(metrics: Metrics): T9CandidateWidthBudget =
        T9CandidateWidthBudget(
            maxWidthPx = metrics.maxRowWidthPx,
            candidateSpacingPx = metrics.candidateSpacingPx,
            candidateHorizontalPaddingPx = metrics.candidateHorizontalPaddingPx,
            minimumCandidateWidthPx = metrics.minimumCandidateWidthPx,
            measureTextWidthPx = measureCandidateTextWidthPx
        )

    fun pinyinSurfacePlan(
        input: SurfaceInput,
        candidateRowWidthPx: Int?
    ): T9PinyinRowSurfacePlanner.Plan? {
        val pinyinState = input.pinyinState ?: return null
        if (pinyinState.items.isEmpty()) return null
        val widths = pinyinRowWidths(input.renderedPinyinItems, input.metrics) ?: return null
        val chipWidthsPx = pinyinChipWidthsPx(pinyinState.items, input.metrics)
        if (candidateRowWidthPx != null) {
            // Decision: keep the Android post-measure correction inside the same geometry seam
            // so the first measured candidate row and later pinyin focus passes use one width rule.
            return T9PinyinRowSurfacePlanner.plan(
                T9PinyinRowSurfacePlanner.Input(
                    candidateMeasuredWidthPx = candidateRowWidthPx,
                    fallbackViewportWidthPx = input.pinyinFallbackViewportWidthPx,
                    state = pinyinState,
                    widths = widths,
                    chipWidthsPx = chipWidthsPx,
                    chipSpacingPx = input.metrics.pinyinChipSpacingPx,
                    maxRowWidthPx = input.metrics.maxRowWidthPx,
                    minVisibleChips = input.metrics.minVisiblePinyinChips,
                    focused = input.pinyinRowFocused
                )
            )
        }
        return surfacePlan(
            input = input,
            pinyinWidths = widths,
            pinyinChipWidthsPx = chipWidthsPx
        ).pinyinSurface
    }

    fun surfacePlan(input: SurfaceInput): T9CandidateSurfacePlanner.Plan =
        surfacePlan(
            input = input,
            pinyinWidths = pinyinRowWidths(input.renderedPinyinItems, input.metrics),
            pinyinChipWidthsPx = input.pinyinState?.items?.let { pinyinChipWidthsPx(it, input.metrics) }.orEmpty()
        )

    private fun surfacePlan(
        input: SurfaceInput,
        pinyinWidths: T9PinyinRowWidthCalculator.Widths?,
        pinyinChipWidthsPx: List<Int>
    ): T9CandidateSurfacePlanner.Plan =
        T9CandidateSurfacePlanner.plan(
            T9CandidateSurfacePlanner.Input(
                candidates = input.candidates,
                widthBudget = widthBudget(input.metrics),
                rowHorizontalPaddingPx = input.metrics.rowHorizontalPaddingPx,
                trailingPaddingPx = input.metrics.trailingPaddingPx,
                showPaginationArrows = input.metrics.showPaginationArrows,
                paginationWidthPx = input.metrics.paginationWidthPx,
                candidateVisualWidthPx = candidateVisualWidthPx,
                pinyinState = input.pinyinState,
                pinyinWidths = pinyinWidths,
                pinyinChipWidthsPx = pinyinChipWidthsPx,
                pinyinChipSpacingPx = input.metrics.pinyinChipSpacingPx,
                pinyinFallbackViewportWidthPx = input.pinyinFallbackViewportWidthPx,
                maxRowWidthPx = input.metrics.maxRowWidthPx,
                minVisiblePinyinChips = input.metrics.minVisiblePinyinChips,
                pinyinRowFocused = input.pinyinRowFocused
            )
        )

    private fun pinyinRowWidths(
        visiblePinyin: List<String>,
        metrics: Metrics
    ): T9PinyinRowWidthCalculator.Widths? {
        visiblePinyin.takeIf { it.isNotEmpty() } ?: return null
        val key = PinyinRowWidthsCacheKey(
            items = visiblePinyin,
            minVisibleChips = metrics.minVisiblePinyinChips,
            chipHorizontalPaddingPx = metrics.pinyinChipHorizontalPaddingPx,
            chipSpacingPx = metrics.pinyinChipSpacingPx,
            overflowHintTextWidthPx = metrics.pinyinOverflowHintTextWidthPx,
            overflowHintSpacingPx = metrics.pinyinOverflowHintSpacingPx,
            foldedEdgeSafetyPx = metrics.pinyinFoldedEdgeSafetyPx
        )
        if (key == pinyinRowWidthsCacheKey) return pinyinRowWidthsCacheValue
        return T9PinyinRowWidthCalculator.calculate(
            T9PinyinRowWidthCalculator.Input(
                items = visiblePinyin,
                minVisibleChips = metrics.minVisiblePinyinChips,
                chipHorizontalPaddingPx = metrics.pinyinChipHorizontalPaddingPx,
                chipSpacingPx = metrics.pinyinChipSpacingPx,
                overflowHintTextWidthPx = metrics.pinyinOverflowHintTextWidthPx,
                overflowHintSpacingPx = metrics.pinyinOverflowHintSpacingPx,
                foldedEdgeSafetyPx = metrics.pinyinFoldedEdgeSafetyPx,
                measureTextWidthPx = ::cachedPinyinTextWidthPx
            )
        )?.also { widths ->
            // Snapshot lists are immutable, but keep a private copy so cache correctness does not
            // depend on an Android caller preserving that implementation detail.
            pinyinRowWidthsCacheKey = key.copy(items = key.items.toList())
            pinyinRowWidthsCacheValue = widths
        }
    }

    private fun pinyinChipWidthsPx(
        items: List<String>,
        metrics: Metrics
    ): List<Int> {
        val key = PinyinChipWidthsCacheKey(
            items = items,
            chipHorizontalPaddingPx = metrics.pinyinChipHorizontalPaddingPx
        )
        if (key == pinyinChipWidthsCacheKey) return pinyinChipWidthsCacheValue
        return items.map { pinyin ->
            cachedPinyinTextWidthPx(pinyin) + metrics.pinyinChipHorizontalPaddingPx * 2
        }.also { widths ->
            pinyinChipWidthsCacheKey = key.copy(items = key.items.toList())
            pinyinChipWidthsCacheValue = widths
        }
    }

    private fun cachedPinyinTextWidthPx(text: String): Int {
        pinyinTextWidthCache[text]?.let { return it }
        if (pinyinTextWidthCache.size >= MAX_PINYIN_TEXT_WIDTH_CACHE_SIZE) {
            pinyinTextWidthCache.keys.firstOrNull()?.let(pinyinTextWidthCache::remove)
        }
        return measurePinyinTextWidthPx(text).also { width ->
            pinyinTextWidthCache[text] = width
        }
    }

    companion object {
        private const val MAX_PINYIN_TEXT_WIDTH_CACHE_SIZE = 256
    }
}

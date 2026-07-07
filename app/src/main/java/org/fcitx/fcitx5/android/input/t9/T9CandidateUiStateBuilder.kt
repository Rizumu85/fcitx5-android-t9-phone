/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation

class T9CandidateUiStateBuilder(
    private val delegate: Delegate
) {
    data class Input(
        val t9InputModeEnabled: Boolean,
        val inputPanel: FcitxEvent.InputPanelEvent.Data,
        val rawPaged: FcitxEvent.PagedCandidateEvent.Data,
        val orientation: FloatingCandidatesOrientation,
        val currentlyVisible: Boolean,
        val loadingState: ChineseT9CandidateLoadingState
    )

    data class ShownState(
        val paged: FcitxEvent.PagedCandidateEvent.Data,
        val originalIndices: IntArray,
        val usesSmartEnglish: Boolean,
        val usesPendingPunctuation: Boolean,
        val usesBulkSelection: Boolean,
        val usesLocalBudget: Boolean,
        val matchedPrefix: String?
    )

    data class Result(
        val renderState: T9CandidateRenderState,
        val shownState: ShownState
    )

    interface Delegate {
        fun getChineseT9InputSnapshot(inputPanel: FcitxEvent.InputPanelEvent.Data): ChineseT9InputSnapshot
        fun isChineseT9InputModeActive(): Boolean
        fun isSmartEnglishT9InputModeActive(): Boolean
        fun getSmartEnglishT9Paged(): FcitxEvent.PagedCandidateEvent.Data?
        fun buildSmartEnglishPaged(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates
        fun getPendingT9PunctuationPaged(): FcitxEvent.PagedCandidateEvent.Data?
        fun buildT9PendingPunctuationPaged(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates
        fun resetT9BulkFilterState()
        fun requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active: Boolean, prefixes: List<String>)
        fun getT9BulkFilterState(): ChineseT9CandidatePipeline.BulkFilterState
        fun filterPagedByT9PinyinPrefixes(
            data: FcitxEvent.PagedCandidateEvent.Data,
            prefixes: List<String>
        ): Pair<T9PagedCandidates, String?>
        fun buildLocalBudgetedPagedFromCurrentPage(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): T9PagedCandidates?
        fun resetT9LocalBudgetState()
        fun buildT9CursorContextSignature(prefixes: List<String>): String
        fun applyT9HanziCursor(
            data: FcitxEvent.PagedCandidateEvent.Data,
            cursorContextSignature: String
        ): FcitxEvent.PagedCandidateEvent.Data
        fun getSmartEnglishT9Presentation(): T9PresentationState?
        fun getT9PresentationState(key: ChineseT9PresentationSnapshotKey): T9PresentationState
        fun clearHiddenChineseT9CompositionIfCandidateUiSuppressed()
        fun effectiveT9CandidateFocus(
            pinyinOptions: List<String>,
            useT9PinyinRow: Boolean
        ): T9CandidateFocus
    }

    private var previousChinesePresentationKey: ChineseT9PresentationSnapshotKey? = null
    private var previousChinesePresentationState: T9PresentationState? = null

    fun build(input: Input): Result? =
        T9ResponsivenessTrace.measure("CandidatesView.updateUi.buildState") {
            val chineseT9Active = delegate.isChineseT9InputModeActive()
            val surface = T9CandidateSourceControlPlanner.surface(
                t9InputModeEnabled = input.t9InputModeEnabled,
                chineseActive = chineseT9Active,
                smartEnglishActive = if (input.t9InputModeEnabled && !chineseT9Active) {
                    delegate.isSmartEnglishT9InputModeActive()
                } else {
                    false
                }
            )
            val chineseSurface = surface == T9CandidateSourceControlPlanner.Surface.CHINESE
            val smartEnglishSurface = surface == T9CandidateSourceControlPlanner.Surface.SMART_ENGLISH
            val chineseSnapshot = if (chineseSurface) {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.chineseSnapshot") {
                    delegate.getChineseT9InputSnapshot(input.inputPanel)
                }
            } else {
                null
            }
            val smartEnglishRawPaged = if (smartEnglishSurface) {
                delegate.getSmartEnglishT9Paged()
            } else {
                null
            }
            val smartEnglishPaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.smartEnglishPage") {
                smartEnglishRawPaged?.let(delegate::buildSmartEnglishPaged)
            }
            val t9FilterPrefixes = chineseSnapshot?.filterPrefixes ?: emptyList()
            val pendingPunctuationPaged = if (chineseSurface || smartEnglishSurface) {
                delegate.getPendingT9PunctuationPaged()
            } else {
                null
            }
            val pendingPunctuationBudgetedPaged = T9ResponsivenessTrace.measure(
                "CandidatesView.updateUi.punctuationPage"
            ) {
                pendingPunctuationPaged?.let(delegate::buildT9PendingPunctuationPaged)
            }
            val pendingT9PinyinSelection = chineseSnapshot?.hasPendingPinyinSelection == true
            val compositionKeyCount = chineseSnapshot?.keyCount ?: 0
            val sourcePlan = T9CandidateSourceControlPlanner.plan(
                T9CandidateSourceControlPlanner.Input(
                    surface = surface,
                    loadingState = input.loadingState,
                    rawCandidatesEmpty = input.rawPaged.candidates.isEmpty(),
                    pendingPunctuationActive = pendingPunctuationPaged != null,
                    compositionKeyCount = compositionKeyCount,
                    pendingPinyinSelection = pendingT9PinyinSelection,
                    filterPrefixesEmpty = t9FilterPrefixes.isEmpty()
                )
            )
            if (sourcePlan.deferRender) {
                return@measure null
            }
            when (sourcePlan.bulkAction) {
                T9CandidateSourceControlPlanner.BulkAction.RESET ->
                    delegate.resetT9BulkFilterState()
                T9CandidateSourceControlPlanner.BulkAction.REQUEST ->
                    T9ResponsivenessTrace.measure("CandidatesView.updateUi.bulkRequest") {
                        delegate.requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active, t9FilterPrefixes)
                    }
            }
            val bulkFilterState = delegate.getT9BulkFilterState()
            val filteredPaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.filterPaged") {
                when (sourcePlan.filterAction) {
                    T9CandidateSourceControlPlanner.FilterAction.EMPTY ->
                        T9PagedCandidates.Empty to null
                    T9CandidateSourceControlPlanner.FilterAction.CHINESE_PREFIX_FILTER ->
                        delegate.filterPagedByT9PinyinPrefixes(input.rawPaged, t9FilterPrefixes)
                    T9CandidateSourceControlPlanner.FilterAction.PASSTHROUGH ->
                        T9PagedCandidates.passthrough(input.rawPaged) to null
                }
            }
            val localBudgetedPaged = if (sourcePlan.shouldBuildLocalBudget(
                    hasBulkFilteredPage = bulkFilterState.paged != null
                )
            ) {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.localBudgetPage") {
                    delegate.buildLocalBudgetedPagedFromCurrentPage(input.rawPaged)
                }
            } else {
                delegate.resetT9LocalBudgetState()
                null
            }
            val presentationPlan = T9ResponsivenessTrace.measure("CandidatesView.updateUi.presentationPlan") {
                T9CandidatePresentationPlanner.plan(
                    T9CandidatePresentationPlanner.Input(
                        rawPaged = input.rawPaged,
                        filteredPaged = filteredPaged.first,
                        filteredMatchedPrefix = filteredPaged.second,
                        smartEnglishPaged = smartEnglishPaged,
                        pendingPunctuationPaged = pendingPunctuationBudgetedPaged,
                        localBudgetedPaged = localBudgetedPaged,
                        bulkFilteredPaged = bulkFilterState.paged,
                        bulkFilteredMatchedPrefix = bulkFilterState.matchedPrefix,
                        bulkFilterPending = bulkFilterState.pending,
                        chineseT9Active = chineseSurface,
                        suppressEmptyCandidates = sourcePlan.suppressEmptyCandidates,
                        pendingPinyinSelection = sourcePlan.pendingPinyinSelection,
                        waitForChineseCandidates = sourcePlan.waitForChineseCandidates
                    )
                )
            }
            val effectivePaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.cursor") {
                if (presentationPlan.applyChineseCursor) {
                    val cursorContextSignature = delegate.buildT9CursorContextSignature(t9FilterPrefixes)
                    delegate.applyT9HanziCursor(presentationPlan.cursorSource.data, cursorContextSignature)
                } else {
                    presentationPlan.cursorSource.data
                }
            }
            val originalIndices = presentationPlan.cursorSource.originalIndices
            val t9State = T9ResponsivenessTrace.measure("CandidatesView.updateUi.presentationState") {
                when (surface) {
                    T9CandidateSourceControlPlanner.Surface.CHINESE -> {
                        if (sourcePlan.suppressEmptyCandidates) {
                            clearChinesePresentationState()
                            null
                        } else {
                            val snapshot = chineseSnapshot ?: return@measure null
                            val key = snapshot.presentationKey(
                                pendingPunctuationText = pendingPunctuationText(
                                    presentationPlan,
                                    effectivePaged
                                ),
                                inputPanel = input.inputPanel,
                                paged = effectivePaged
                            )
                            getChinesePresentationState(key)
                        }
                    }
                    T9CandidateSourceControlPlanner.Surface.SMART_ENGLISH -> {
                        clearChinesePresentationState()
                        // Punctuation preview is mode-neutral: English users need the same top
                        // bubble confirmation as Chinese before committing a symbol candidate.
                        pendingPunctuationText(presentationPlan, effectivePaged)
                            ?.let(::pendingPunctuationPresentationState)
                            ?: delegate.getSmartEnglishT9Presentation()
                    }
                    T9CandidateSourceControlPlanner.Surface.OTHER -> {
                        clearChinesePresentationState()
                        null
                    }
                }
            }
            if (sourcePlan.suppressEmptyCandidates) {
                delegate.clearHiddenChineseT9CompositionIfCandidateUiSuppressed()
            }
            val pinyinOptions = t9State?.pinyinOptions ?: emptyList()
            val shownState = ShownState(
                paged = effectivePaged,
                originalIndices = originalIndices,
                usesSmartEnglish = presentationPlan.usesSmartEnglish,
                usesPendingPunctuation = presentationPlan.usesPendingPunctuation,
                usesBulkSelection = presentationPlan.usesBulkSelection,
                usesLocalBudget = presentationPlan.usesLocalBudget,
                matchedPrefix = presentationPlan.matchedPrefix
            )
            val focus = delegate.effectiveT9CandidateFocus(
                pinyinOptions = pinyinOptions,
                useT9PinyinRow = chineseSurface
            )
            Result(
                renderState = T9CandidateRenderStatePlanner.plan(
                    T9CandidateRenderStatePlanner.Input(
                        inputPanel = input.inputPanel,
                        candidates = effectivePaged,
                        orientation = input.orientation,
                        usesSmartEnglish = shownState.usesSmartEnglish,
                        usesPendingPunctuation = shownState.usesPendingPunctuation,
                        chineseT9Active = chineseSurface,
                        suppressEmptyCandidates = sourcePlan.suppressEmptyCandidates,
                        presentationState = t9State,
                        focus = focus
                    )
                ),
                shownState = shownState
            )
        }

    private fun getChinesePresentationState(key: ChineseT9PresentationSnapshotKey): T9PresentationState {
        if (key == previousChinesePresentationKey) {
            previousChinesePresentationState?.let { return it }
        }
        return delegate.getT9PresentationState(key).also {
            previousChinesePresentationKey = key
            previousChinesePresentationState = it
        }
    }

    private fun clearChinesePresentationState() {
        previousChinesePresentationKey = null
        previousChinesePresentationState = null
    }

    private fun pendingPunctuationText(
        presentationPlan: T9CandidatePresentationPlanner.Plan,
        effectivePaged: FcitxEvent.PagedCandidateEvent.Data
    ): String? {
        if (!presentationPlan.usesPendingPunctuation) return null
        return effectivePaged.candidates.getOrNull(effectivePaged.cursorIndex)?.text
            ?: effectivePaged.candidates.firstOrNull()?.text
    }

    private fun pendingPunctuationPresentationState(text: String): T9PresentationState =
        T9PresentationState(
            topReading = formattedText(text),
            pinyinOptions = emptyList()
        )

    private fun formattedText(text: String): FormattedText? =
        if (text.isEmpty()) {
            null
        } else {
            FormattedText(
                strings = arrayOf(text),
                flags = intArrayOf(TextFormatFlag.NoFlag.flag),
                cursor = -1
            )
        }

}

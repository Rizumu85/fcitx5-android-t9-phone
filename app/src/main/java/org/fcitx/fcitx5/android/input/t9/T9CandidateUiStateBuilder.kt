/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation

class T9CandidateUiStateBuilder(
    private val delegate: Delegate
) {
    private enum class Surface {
        CHINESE,
        SMART_ENGLISH,
        OTHER
    }

    data class Input(
        val t9InputModeEnabled: Boolean,
        val inputPanel: FcitxEvent.InputPanelEvent.Data,
        val rawPaged: FcitxEvent.PagedCandidateEvent.Data,
        val orientation: FloatingCandidatesOrientation,
        val currentlyVisible: Boolean,
        val loadingState: ChineseT9CandidateLoadingState,
        val bulkFilteredPaged: T9PagedCandidates?,
        val bulkFilteredMatchedPrefix: String?,
        val bulkFilterPending: Boolean
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
        fun getT9PresentationState(
            snapshot: ChineseT9InputSnapshot,
            inputPanel: FcitxEvent.InputPanelEvent.Data,
            effectivePaged: FcitxEvent.PagedCandidateEvent.Data
        ): T9PresentationState
        fun clearHiddenChineseT9CompositionIfCandidateUiSuppressed()
        fun effectiveT9CandidateFocus(
            pinyinOptions: List<String>,
            useT9PinyinRow: Boolean
        ): T9CandidateFocus
    }

    fun build(input: Input): Result? =
        T9ResponsivenessTrace.measure("CandidatesView.updateUi.buildState") {
            val (chineseT9Active, smartEnglishT9Active, surface) = T9ResponsivenessTrace.measure(
                "CandidatesView.updateUi.modeState"
            ) {
                val chineseActive = input.t9InputModeEnabled && delegate.isChineseT9InputModeActive()
                val smartEnglishActive = !chineseActive &&
                    input.t9InputModeEnabled &&
                    delegate.isSmartEnglishT9InputModeActive()
                Triple(
                    chineseActive,
                    smartEnglishActive,
                    when {
                        chineseActive -> Surface.CHINESE
                        smartEnglishActive -> Surface.SMART_ENGLISH
                        else -> Surface.OTHER
                    }
                )
            }
            val chineseSnapshot = if (surface == Surface.CHINESE) {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.chineseSnapshot") {
                    delegate.getChineseT9InputSnapshot(input.inputPanel)
                }
            } else {
                null
            }
            val smartEnglishRawPaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.smartEnglishRaw") {
                if (surface == Surface.SMART_ENGLISH) {
                    delegate.getSmartEnglishT9Paged()
                } else {
                    null
                }
            }
            val smartEnglishPaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.smartEnglishPage") {
                smartEnglishRawPaged?.let(delegate::buildSmartEnglishPaged)
            }
            val t9FilterPrefixes = T9ResponsivenessTrace.measure("CandidatesView.updateUi.pinyinPrefixes") {
                chineseSnapshot?.filterPrefixes ?: emptyList()
            }
            val pendingPunctuationPaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.punctuationRaw") {
                if (surface == Surface.CHINESE || surface == Surface.SMART_ENGLISH) {
                    delegate.getPendingT9PunctuationPaged()
                } else {
                    null
                }
            }
            val pendingPunctuationBudgetedPaged = T9ResponsivenessTrace.measure(
                "CandidatesView.updateUi.punctuationPage"
            ) {
                pendingPunctuationPaged?.let(delegate::buildT9PendingPunctuationPaged)
            }
            val pendingT9PinyinSelection = T9ResponsivenessTrace.measure(
                "CandidatesView.updateUi.pendingPinyinSelection"
            ) {
                chineseSnapshot?.hasPendingPinyinSelection == true
            }
            val compositionKeyCount = T9ResponsivenessTrace.measure("CandidatesView.updateUi.compositionKeyCount") {
                chineseSnapshot?.keyCount ?: 0
            }
            val waitForChineseT9Candidates = T9ResponsivenessTrace.measure("CandidatesView.updateUi.loadingDecision") {
                if (surface == Surface.CHINESE) {
                    input.loadingState.shouldWaitForCandidates(
                        chineseT9Active = true,
                        compositionKeyCount = compositionKeyCount,
                        hasPendingPunctuation = pendingPunctuationPaged != null,
                        pendingPinyinSelection = pendingT9PinyinSelection,
                        rawCandidatesEmpty = input.rawPaged.candidates.isEmpty()
                    )
                } else {
                    false
                }
            }
            if (surface == Surface.CHINESE &&
                waitForChineseT9Candidates &&
                pendingPunctuationPaged == null &&
                input.currentlyVisible
            ) {
                return@measure null
            }
            val suppressEmptyT9Candidates = surface == Surface.CHINESE &&
                pendingPunctuationPaged == null &&
                compositionKeyCount <= 0
            when (
                T9CandidateUiEffectPlanner.bulkEffect(
                    isChineseSurface = surface == Surface.CHINESE,
                    suppressEmptyCandidates = suppressEmptyT9Candidates,
                    pendingPinyinSelection = pendingT9PinyinSelection,
                    waitForChineseCandidates = waitForChineseT9Candidates,
                    hasPendingPunctuation = pendingPunctuationPaged != null
                )
            ) {
                T9CandidateUiEffectPlanner.BulkEffect.RESET ->
                    T9ResponsivenessTrace.measure("CandidatesView.updateUi.bulkReset") {
                        delegate.resetT9BulkFilterState()
                    }
                T9CandidateUiEffectPlanner.BulkEffect.REQUEST ->
                    T9ResponsivenessTrace.measure("CandidatesView.updateUi.bulkRequest") {
                        delegate.requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active, t9FilterPrefixes)
                    }
                T9CandidateUiEffectPlanner.BulkEffect.NONE -> Unit
            }
            val filteredPaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.filterPaged") {
                if (suppressEmptyT9Candidates || pendingT9PinyinSelection || waitForChineseT9Candidates) {
                    T9PagedCandidates.Empty to null
                } else if (chineseT9Active) {
                    delegate.filterPagedByT9PinyinPrefixes(input.rawPaged, t9FilterPrefixes)
                } else {
                    T9PagedCandidates.passthrough(input.rawPaged) to null
                }
            }
            val localBudgetDecision = T9CandidateUiEffectPlanner.localBudgetDecision(
                suppressEmptyCandidates = suppressEmptyT9Candidates,
                pendingPinyinSelection = pendingT9PinyinSelection,
                hasPendingPunctuation = pendingPunctuationPaged != null,
                chineseT9Active = chineseT9Active,
                hasFilterPrefixes = t9FilterPrefixes.isNotEmpty(),
                hasBulkFilteredPage = input.bulkFilteredPaged != null,
                waitForChineseCandidates = waitForChineseT9Candidates
            )
            val localBudgetedPaged = if (localBudgetDecision.buildLocalBudgetPage) {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.localBudgetPage") {
                    delegate.buildLocalBudgetedPagedFromCurrentPage(input.rawPaged)
                }
            } else if (localBudgetDecision.resetLocalBudget) {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.localBudgetReset") {
                    delegate.resetT9LocalBudgetState()
                }
                null
            } else {
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
                        bulkFilteredPaged = input.bulkFilteredPaged,
                        bulkFilteredMatchedPrefix = input.bulkFilteredMatchedPrefix,
                        bulkFilterPending = input.bulkFilterPending,
                        chineseT9Active = chineseT9Active,
                        suppressEmptyCandidates = suppressEmptyT9Candidates,
                        pendingPinyinSelection = pendingT9PinyinSelection,
                        waitForChineseCandidates = waitForChineseT9Candidates
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
                    Surface.CHINESE -> {
                        if (suppressEmptyT9Candidates) {
                            null
                        } else {
                            delegate.getT9PresentationState(
                                chineseSnapshot ?: return@measure null,
                                input.inputPanel,
                                effectivePaged
                            )
                        }
                    }
                    Surface.SMART_ENGLISH -> delegate.getSmartEnglishT9Presentation()
                    Surface.OTHER -> null
                }
            }
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.finalState") {
                val nextPreferAboveCursorAnchor = false
                val panelToShow = T9ResponsivenessTrace.measure("CandidatesView.updateUi.finalState.panel") {
                    if (suppressEmptyT9Candidates) {
                        T9ResponsivenessTrace.measure("CandidatesView.updateUi.finalState.hiddenClear") {
                            delegate.clearHiddenChineseT9CompositionIfCandidateUiSuppressed()
                        }
                        FcitxEvent.InputPanelEvent.Data()
                    } else {
                        t9State?.topReading?.let {
                            FcitxEvent.InputPanelEvent.Data(it, input.inputPanel.auxUp, input.inputPanel.auxDown)
                        } ?: input.inputPanel
                    }
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
                val focus = T9ResponsivenessTrace.measure("CandidatesView.updateUi.finalState.focus") {
                    delegate.effectiveT9CandidateFocus(
                        pinyinOptions = pinyinOptions,
                        useT9PinyinRow = chineseT9Active
                    )
                }
                val shouldShow = T9ResponsivenessTrace.measure("CandidatesView.updateUi.finalState.shouldShow") {
                    shouldShowT9CandidateUi(
                        inputPanel = input.inputPanel,
                        suppressEmptyT9Candidates = suppressEmptyT9Candidates,
                        t9State = t9State,
                        effectivePaged = effectivePaged
                    )
                }
                Result(
                    renderState = T9CandidateRenderState(
                        panel = panelToShow,
                        candidates = effectivePaged,
                        orientation = input.orientation,
                        showShortcutLabels = shouldShowT9BottomShortcutLabels(effectivePaged, shownState, chineseT9Active),
                        pinyinOptions = pinyinOptions,
                        pinyinUseT9 = chineseT9Active,
                        focus = focus,
                        preferAboveCursorAnchor = nextPreferAboveCursorAnchor,
                        shouldShow = shouldShow
                    ),
                    shownState = shownState
                )
            }
        }

    private fun shouldShowT9BottomShortcutLabels(
        data: FcitxEvent.PagedCandidateEvent.Data,
        shownState: ShownState,
        chineseT9Active: Boolean
    ): Boolean =
        data.candidates.isNotEmpty() &&
            (shownState.usesSmartEnglish || shownState.usesPendingPunctuation || chineseT9Active)

    private fun shouldShowT9CandidateUi(
        inputPanel: FcitxEvent.InputPanelEvent.Data,
        suppressEmptyT9Candidates: Boolean,
        t9State: T9PresentationState?,
        effectivePaged: FcitxEvent.PagedCandidateEvent.Data
    ): Boolean =
        !suppressEmptyT9Candidates && evaluateVisibility(
            inputPanel = inputPanel,
            topReading = t9State?.topReading,
            pinyinRowVisible = t9State?.pinyinRowVisible == true,
            hasVisibleCandidates = effectivePaged.candidates.isNotEmpty()
        )

    private fun evaluateVisibility(
        inputPanel: FcitxEvent.InputPanelEvent.Data,
        topReading: FormattedText?,
        pinyinRowVisible: Boolean,
        hasVisibleCandidates: Boolean
    ): Boolean =
        inputPanel.preedit.isNotEmpty() ||
            hasVisibleCandidates ||
            inputPanel.auxUp.isNotEmpty() ||
            inputPanel.auxDown.isNotEmpty() ||
            topReading?.isNotEmpty() == true ||
            pinyinRowVisible
}

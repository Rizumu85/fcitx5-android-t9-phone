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
    data class Input(
        val t9InputModeEnabled: Boolean,
        val inputPanel: FcitxEvent.InputPanelEvent.Data,
        val rawPaged: FcitxEvent.PagedCandidateEvent.Data,
        val orientation: FloatingCandidatesOrientation,
        val currentlyVisible: Boolean,
        val loadingState: ChineseT9CandidateLoadingState,
        val bulkFilteredPaged: FcitxEvent.PagedCandidateEvent.Data?,
        val bulkFilteredOriginalIndices: IntArray,
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
        fun syncT9CompositionWithInputPanel(inputPanel: FcitxEvent.InputPanelEvent.Data)
        fun isChineseT9InputModeActive(): Boolean
        fun isSmartEnglishT9InputModeActive(): Boolean
        fun getSmartEnglishT9Paged(): FcitxEvent.PagedCandidateEvent.Data?
        fun buildSmartEnglishPaged(data: FcitxEvent.PagedCandidateEvent.Data): FcitxEvent.PagedCandidateEvent.Data
        fun getT9ResolvedPinyinFilterPrefixes(): List<String>
        fun getPendingT9PunctuationPaged(): FcitxEvent.PagedCandidateEvent.Data?
        fun buildT9PendingPunctuationPaged(data: FcitxEvent.PagedCandidateEvent.Data): FcitxEvent.PagedCandidateEvent.Data
        fun hasPendingT9PinyinSelection(): Boolean
        fun getT9CompositionKeyCount(): Int
        fun resetT9BulkFilterState()
        fun requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active: Boolean, prefixes: List<String>)
        fun filterPagedByT9PinyinPrefixes(
            data: FcitxEvent.PagedCandidateEvent.Data,
            prefixes: List<String>
        ): Pair<FcitxEvent.PagedCandidateEvent.Data, String?>
        fun buildLocalBudgetedPagedFromCurrentPage(
            data: FcitxEvent.PagedCandidateEvent.Data
        ): FcitxEvent.PagedCandidateEvent.Data?
        fun resetT9LocalBudgetState()
        fun buildT9CursorContextSignature(prefixes: List<String>): String
        fun applyT9HanziCursor(
            data: FcitxEvent.PagedCandidateEvent.Data,
            cursorContextSignature: String
        ): FcitxEvent.PagedCandidateEvent.Data
        fun buildOriginalIndicesForPendingPunctuation(shown: FcitxEvent.PagedCandidateEvent.Data): IntArray
        fun buildOriginalIndicesForSmartEnglish(shown: FcitxEvent.PagedCandidateEvent.Data): IntArray
        fun buildOriginalIndicesForPaged(shown: FcitxEvent.PagedCandidateEvent.Data): IntArray
        fun getSmartEnglishT9Presentation(): T9PresentationState?
        fun getT9PresentationState(
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
            if (input.t9InputModeEnabled) {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.syncComposition") {
                    delegate.syncT9CompositionWithInputPanel(input.inputPanel)
                }
            }
            val chineseT9Active = input.t9InputModeEnabled && delegate.isChineseT9InputModeActive()
            val smartEnglishRawPaged = delegate.getSmartEnglishT9Paged()
            val smartEnglishPaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.smartEnglishPage") {
                smartEnglishRawPaged?.let(delegate::buildSmartEnglishPaged)
            }
            val t9FilterPrefixes = if (chineseT9Active) {
                delegate.getT9ResolvedPinyinFilterPrefixes()
            } else {
                emptyList()
            }
            val pendingPunctuationPaged = if (chineseT9Active || delegate.isSmartEnglishT9InputModeActive()) {
                delegate.getPendingT9PunctuationPaged()
            } else {
                null
            }
            val pendingPunctuationBudgetedPaged = T9ResponsivenessTrace.measure(
                "CandidatesView.updateUi.punctuationPage"
            ) {
                pendingPunctuationPaged?.let(delegate::buildT9PendingPunctuationPaged)
            }
            val pendingT9PinyinSelection = chineseT9Active && delegate.hasPendingT9PinyinSelection()
            val compositionKeyCount = delegate.getT9CompositionKeyCount()
            val waitForChineseT9Candidates = input.loadingState.shouldWaitForCandidates(
                chineseT9Active = chineseT9Active,
                compositionKeyCount = compositionKeyCount,
                hasPendingPunctuation = pendingPunctuationPaged != null,
                pendingPinyinSelection = pendingT9PinyinSelection,
                rawCandidatesEmpty = input.rawPaged.candidates.isEmpty()
            )
            if (waitForChineseT9Candidates && input.currentlyVisible) {
                return@measure null
            }
            val suppressEmptyT9Candidates = chineseT9Active &&
                pendingPunctuationPaged == null &&
                compositionKeyCount <= 0
            if (suppressEmptyT9Candidates || pendingT9PinyinSelection || waitForChineseT9Candidates) {
                delegate.resetT9BulkFilterState()
            } else if (pendingPunctuationPaged == null) {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.bulkRequest") {
                    delegate.requestT9BulkFilteredCandidatesIfNeeded(chineseT9Active, t9FilterPrefixes)
                }
            } else {
                delegate.resetT9BulkFilterState()
            }
            val filteredPaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.filterPaged") {
                if (suppressEmptyT9Candidates || pendingT9PinyinSelection || waitForChineseT9Candidates) {
                    FcitxEvent.PagedCandidateEvent.Data.Empty to null
                } else if (chineseT9Active) {
                    delegate.filterPagedByT9PinyinPrefixes(input.rawPaged, t9FilterPrefixes)
                } else {
                    input.rawPaged to null
                }
            }
            val localBudgetedPaged = if (
                !suppressEmptyT9Candidates &&
                !pendingT9PinyinSelection &&
                pendingPunctuationPaged == null &&
                chineseT9Active &&
                t9FilterPrefixes.isEmpty() &&
                input.bulkFilteredPaged == null &&
                !waitForChineseT9Candidates
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
            val cursorContextSignature = delegate.buildT9CursorContextSignature(t9FilterPrefixes)
            val effectivePaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.cursor") {
                if (presentationPlan.applyChineseCursor) {
                    delegate.applyT9HanziCursor(presentationPlan.cursorSource, cursorContextSignature)
                } else {
                    presentationPlan.cursorSource
                }
            }
            val originalIndices = T9ResponsivenessTrace.measure("CandidatesView.updateUi.originalIndices") {
                when (presentationPlan.originalIndexSource) {
                    T9CandidatePresentationPlanner.OriginalIndexSource.PENDING_PUNCTUATION ->
                        delegate.buildOriginalIndicesForPendingPunctuation(effectivePaged)
                    T9CandidatePresentationPlanner.OriginalIndexSource.SMART_ENGLISH ->
                        delegate.buildOriginalIndicesForSmartEnglish(effectivePaged)
                    T9CandidatePresentationPlanner.OriginalIndexSource.BULK_FILTERED ->
                        input.bulkFilteredOriginalIndices
                    T9CandidatePresentationPlanner.OriginalIndexSource.PENDING_BULK_DISPLAY ->
                        IntArray(effectivePaged.candidates.size) { -1 }
                    T9CandidatePresentationPlanner.OriginalIndexSource.LOCAL_BUDGET ->
                        delegate.buildOriginalIndicesForPaged(presentationPlan.candidateSource)
                    T9CandidatePresentationPlanner.OriginalIndexSource.PAGED ->
                        delegate.buildOriginalIndicesForPaged(effectivePaged)
                }
            }
            val t9State = T9ResponsivenessTrace.measure("CandidatesView.updateUi.presentationState") {
                delegate.getSmartEnglishT9Presentation() ?: if (chineseT9Active) {
                    delegate.getT9PresentationState(input.inputPanel, effectivePaged)
                } else {
                    null
                }
            }
            val nextPreferAboveCursorAnchor = input.t9InputModeEnabled &&
                delegate.isChineseT9InputModeActive() &&
                (
                    pendingPunctuationPaged != null ||
                        compositionKeyCount > 0 ||
                        t9State?.topReading?.isNotEmpty() == true ||
                        t9State?.pinyinRowVisible == true
                    )
            val panelToShow = if (suppressEmptyT9Candidates) {
                delegate.clearHiddenChineseT9CompositionIfCandidateUiSuppressed()
                FcitxEvent.InputPanelEvent.Data()
            } else {
                t9State?.topReading?.let {
                    FcitxEvent.InputPanelEvent.Data(it, input.inputPanel.auxUp, input.inputPanel.auxDown)
                } ?: input.inputPanel
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
            Result(
                renderState = T9CandidateRenderState(
                    panel = panelToShow,
                    candidates = effectivePaged,
                    orientation = input.orientation,
                    showShortcutLabels = shouldShowT9BottomShortcutLabels(effectivePaged, shownState, chineseT9Active),
                    pinyinOptions = pinyinOptions,
                    pinyinUseT9 = chineseT9Active,
                    focus = delegate.effectiveT9CandidateFocus(
                        pinyinOptions = pinyinOptions,
                        useT9PinyinRow = chineseT9Active
                    ),
                    preferAboveCursorAnchor = nextPreferAboveCursorAnchor,
                    shouldShow = shouldShowT9CandidateUi(
                        inputPanel = input.inputPanel,
                        suppressEmptyT9Candidates = suppressEmptyT9Candidates,
                        waitForChineseT9Candidates = waitForChineseT9Candidates,
                        t9State = t9State,
                        effectivePaged = effectivePaged
                    )
                ),
                shownState = shownState
            )
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
        waitForChineseT9Candidates: Boolean,
        t9State: T9PresentationState?,
        effectivePaged: FcitxEvent.PagedCandidateEvent.Data
    ): Boolean =
        !suppressEmptyT9Candidates && !waitForChineseT9Candidates && evaluateVisibility(
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

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
        fun syncT9CompositionWithInputPanel(inputPanel: FcitxEvent.InputPanelEvent.Data)
        fun isChineseT9InputModeActive(): Boolean
        fun isSmartEnglishT9InputModeActive(): Boolean
        fun getSmartEnglishT9Paged(): FcitxEvent.PagedCandidateEvent.Data?
        fun buildSmartEnglishPaged(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates
        fun getT9ResolvedPinyinFilterPrefixes(): List<String>
        fun getPendingT9PunctuationPaged(): FcitxEvent.PagedCandidateEvent.Data?
        fun buildT9PendingPunctuationPaged(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates
        fun hasPendingT9PinyinSelection(): Boolean
        fun getT9CompositionKeyCount(): Int
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
            val chineseT9Active = input.t9InputModeEnabled && delegate.isChineseT9InputModeActive()
            val smartEnglishT9Active = !chineseT9Active &&
                input.t9InputModeEnabled &&
                delegate.isSmartEnglishT9InputModeActive()
            val surface = when {
                chineseT9Active -> Surface.CHINESE
                smartEnglishT9Active -> Surface.SMART_ENGLISH
                else -> Surface.OTHER
            }
            if (surface == Surface.CHINESE) {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.syncComposition") {
                    delegate.syncT9CompositionWithInputPanel(input.inputPanel)
                }
            }
            val smartEnglishRawPaged = if (surface == Surface.SMART_ENGLISH) {
                delegate.getSmartEnglishT9Paged()
            } else {
                null
            }
            val smartEnglishPaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.smartEnglishPage") {
                smartEnglishRawPaged?.let(delegate::buildSmartEnglishPaged)
            }
            val t9FilterPrefixes = if (surface == Surface.CHINESE) {
                delegate.getT9ResolvedPinyinFilterPrefixes()
            } else {
                emptyList()
            }
            val pendingPunctuationPaged = if (surface == Surface.CHINESE || surface == Surface.SMART_ENGLISH) {
                delegate.getPendingT9PunctuationPaged()
            } else {
                null
            }
            val pendingPunctuationBudgetedPaged = T9ResponsivenessTrace.measure(
                "CandidatesView.updateUi.punctuationPage"
            ) {
                pendingPunctuationPaged?.let(delegate::buildT9PendingPunctuationPaged)
            }
            val pendingT9PinyinSelection = surface == Surface.CHINESE && delegate.hasPendingT9PinyinSelection()
            val compositionKeyCount = if (surface == Surface.CHINESE) {
                delegate.getT9CompositionKeyCount()
            } else {
                0
            }
            val waitForChineseT9Candidates = if (surface == Surface.CHINESE) {
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
            if (waitForChineseT9Candidates && input.currentlyVisible) {
                return@measure null
            }
            val suppressEmptyT9Candidates = surface == Surface.CHINESE &&
                pendingPunctuationPaged == null &&
                compositionKeyCount <= 0
            if (
                surface != Surface.CHINESE ||
                suppressEmptyT9Candidates ||
                pendingT9PinyinSelection ||
                waitForChineseT9Candidates
            ) {
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
                    T9PagedCandidates.Empty to null
                } else if (chineseT9Active) {
                    delegate.filterPagedByT9PinyinPrefixes(input.rawPaged, t9FilterPrefixes)
                } else {
                    T9PagedCandidates.passthrough(input.rawPaged) to null
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
                    Surface.CHINESE -> delegate.getT9PresentationState(input.inputPanel, effectivePaged)
                    Surface.SMART_ENGLISH -> delegate.getSmartEnglishT9Presentation()
                    Surface.OTHER -> null
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

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation

data class T9CandidateUiInputSnapshot(
    val t9InputModeEnabled: Boolean,
    val inputPanel: FcitxEvent.InputPanelEvent.Data,
    val rawPaged: FcitxEvent.PagedCandidateEvent.Data,
    val orientation: FloatingCandidatesOrientation,
    val currentlyVisible: Boolean,
    val loadingState: ChineseT9CandidateLoadingState,
    val widthBudget: T9CandidateWidthBudget?,
    val chineseT9Active: Boolean,
    val smartEnglishActive: Boolean,
    val chineseSnapshot: ChineseT9InputSnapshot?,
    val smartEnglishRawPaged: FcitxEvent.PagedCandidateEvent.Data?,
    val pendingPunctuationRawPaged: FcitxEvent.PagedCandidateEvent.Data?,
    val smartEnglishPresentation: T9PresentationState?,
    val currentFocus: T9CandidateFocus
)

data class T9CandidateInteractionState(
    val shownSource: T9CandidateUiSnapshotPipeline.ShownSource,
    val hasBottomCandidateRow: Boolean,
    val matchedPrefix: String?
)

data class T9CandidateUiSnapshot(
    val renderState: T9CandidateRenderState,
    val shownState: T9CandidateUiStateBuilder.ShownState,
    val interactionState: T9CandidateInteractionState,
    val focusCorrection: T9CandidateFocus?
)

class T9CandidateUiStateBuilder(
    private val pipeline: Pipeline
) {
    data class ShownState(
        val paged: FcitxEvent.PagedCandidateEvent.Data,
        val originalIndices: IntArray,
        val usesSmartEnglish: Boolean,
        val usesPendingPunctuation: Boolean,
        val usesBulkSelection: Boolean,
        val usesLocalBudget: Boolean,
        val matchedPrefix: String?
    )

    interface Pipeline {
        fun buildSmartEnglishPaged(data: FcitxEvent.PagedCandidateEvent.Data): T9PagedCandidates
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
        fun buildT9CursorContextSignature(preedit: CharSequence, prefixes: List<String>): String
        fun applyT9HanziCursor(
            data: FcitxEvent.PagedCandidateEvent.Data,
            cursorContextSignature: String
        ): FcitxEvent.PagedCandidateEvent.Data
        fun getT9PresentationState(key: ChineseT9PresentationSnapshotKey): T9PresentationState
        fun clearHiddenChineseT9CompositionIfCandidateUiSuppressed()
    }

    private var previousChinesePresentationKey: ChineseT9PresentationSnapshotKey? = null
    private var previousChinesePresentationState: T9PresentationState? = null

    fun build(input: T9CandidateUiInputSnapshot): T9CandidateUiSnapshot? =
        T9ResponsivenessTrace.measure("CandidatesView.updateUi.buildState") {
            val surface = T9CandidateSourceControlPlanner.surface(
                t9InputModeEnabled = input.t9InputModeEnabled,
                chineseActive = input.chineseT9Active,
                smartEnglishActive = if (input.t9InputModeEnabled && !input.chineseT9Active) {
                    input.smartEnglishActive
                } else {
                    false
                }
            )
            val chineseSurface = surface == T9CandidateSourceControlPlanner.Surface.CHINESE
            val smartEnglishSurface = surface == T9CandidateSourceControlPlanner.Surface.SMART_ENGLISH
            val chineseSnapshot = if (chineseSurface) {
                input.chineseSnapshot
            } else {
                null
            }
            val smartEnglishRawPaged = if (smartEnglishSurface) {
                input.smartEnglishRawPaged
            } else {
                null
            }
            val smartEnglishPaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.smartEnglishPage") {
                smartEnglishRawPaged?.let(pipeline::buildSmartEnglishPaged)
            }
            val t9FilterPrefixes = chineseSnapshot?.filterPrefixes ?: emptyList()
            val pendingPunctuationPaged = if (chineseSurface || smartEnglishSurface) {
                input.pendingPunctuationRawPaged
            } else {
                null
            }
            val pendingPunctuationBudgetedPaged = T9ResponsivenessTrace.measure(
                "CandidatesView.updateUi.punctuationPage"
            ) {
                pendingPunctuationPaged?.let(pipeline::buildT9PendingPunctuationPaged)
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
                    pipeline.resetT9BulkFilterState()
                T9CandidateSourceControlPlanner.BulkAction.REQUEST ->
                    T9ResponsivenessTrace.measure("CandidatesView.updateUi.bulkRequest") {
                        pipeline.requestT9BulkFilteredCandidatesIfNeeded(input.chineseT9Active, t9FilterPrefixes)
                    }
            }
            val bulkFilterState = pipeline.getT9BulkFilterState()
            if (ChineseT9CandidateFrameGate.shouldDefer(
                    ChineseT9CandidateFrameGate.Input(
                        chineseSurface = chineseSurface,
                        engineCandidatesPending = sourcePlan.deferRender,
                        bulkCandidatesPending = bulkFilterState.pending,
                        hasBulkCandidatePage = bulkFilterState.paged != null
                    )
                )
            ) {
                return@measure null
            }
            val filteredPaged = T9ResponsivenessTrace.measure("CandidatesView.updateUi.filterPaged") {
                when (sourcePlan.filterAction) {
                    T9CandidateSourceControlPlanner.FilterAction.EMPTY ->
                        T9PagedCandidates.Empty to null
                    T9CandidateSourceControlPlanner.FilterAction.CHINESE_PREFIX_FILTER ->
                        pipeline.filterPagedByT9PinyinPrefixes(input.rawPaged, t9FilterPrefixes)
                    T9CandidateSourceControlPlanner.FilterAction.PASSTHROUGH ->
                        T9PagedCandidates.passthrough(input.rawPaged) to null
                }
            }
            val localBudgetedPaged = if (sourcePlan.shouldBuildLocalBudget(
                    hasBulkFilteredPage = bulkFilterState.paged != null,
                    bulkFilterPending = bulkFilterState.pending
                )
            ) {
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.localBudgetPage") {
                    pipeline.buildLocalBudgetedPagedFromCurrentPage(input.rawPaged)
                }
            } else {
                pipeline.resetT9LocalBudgetState()
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
                    val cursorContextSignature = pipeline.buildT9CursorContextSignature(
                        input.inputPanel.preedit.toString(),
                        t9FilterPrefixes
                    )
                    pipeline.applyT9HanziCursor(presentationPlan.cursorSource.data, cursorContextSignature)
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
                            ?: input.smartEnglishPresentation
                    }
                    T9CandidateSourceControlPlanner.Surface.OTHER -> {
                        clearChinesePresentationState()
                        null
                    }
                }
            }
            if (sourcePlan.suppressEmptyCandidates) {
                pipeline.clearHiddenChineseT9CompositionIfCandidateUiSuppressed()
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
            val focusPlan = effectiveT9CandidateFocus(
                current = input.currentFocus,
                pinyinOptions = pinyinOptions,
                useT9PinyinRow = chineseSurface
            )
            T9CandidateUiSnapshot(
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
                        focus = focusPlan.focus
                    )
                ),
                shownState = shownState,
                interactionState = T9CandidateInteractionState(
                    shownSource = when {
                        shownState.usesPendingPunctuation ->
                            T9CandidateUiSnapshotPipeline.ShownSource.PENDING_PUNCTUATION
                        shownState.usesSmartEnglish ->
                            T9CandidateUiSnapshotPipeline.ShownSource.SMART_ENGLISH
                        shownState.usesBulkSelection ->
                            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_BULK
                        chineseSurface && shownState.usesLocalBudget ->
                            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_LOCAL
                        chineseSurface ->
                            T9CandidateUiSnapshotPipeline.ShownSource.CHINESE_ENGINE
                        else -> T9CandidateUiSnapshotPipeline.ShownSource.OTHER
                    },
                    hasBottomCandidateRow = effectivePaged.candidates.isNotEmpty(),
                    matchedPrefix = shownState.matchedPrefix
                ),
                focusCorrection = focusPlan.correction
            )
        }

    private fun getChinesePresentationState(key: ChineseT9PresentationSnapshotKey): T9PresentationState {
        if (key == previousChinesePresentationKey) {
            previousChinesePresentationState?.let { return it }
        }
        return pipeline.getT9PresentationState(key).also {
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

    private data class FocusPlan(
        val focus: T9CandidateFocus,
        val correction: T9CandidateFocus?
    )

    private fun effectiveT9CandidateFocus(
        current: T9CandidateFocus,
        pinyinOptions: List<String>,
        useT9PinyinRow: Boolean
    ): FocusPlan {
        if (useT9PinyinRow && pinyinOptions.isNotEmpty()) return FocusPlan(current, null)
        return FocusPlan(
            focus = T9CandidateFocus.BOTTOM,
            correction = T9CandidateFocus.BOTTOM.takeIf { current == T9CandidateFocus.TOP }
        )
    }

}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

object T9CandidateRenderPassPlanner {
    enum class PinyinAction {
        NONE,
        CLEAR,
        RENDER,
        SYNC_LAYOUT
    }

    data class Input(
        val previousState: T9CandidateRenderState?,
        val nextState: T9CandidateRenderState,
        val patch: T9CandidateRenderPatch,
        val previousVisibilityRequest: T9CandidateVisibilityPlanner.Request?
    )

    data class Plan(
        val pinyinAction: PinyinAction,
        val fallbackContentReady: Boolean,
        val hiddenVisibilityRequest: T9CandidateVisibilityPlanner.Request?,
        val hiddenVisibilityAction: T9CandidateVisibilityPlanner.Action
    ) {
        val skipChildRender: Boolean
            get() = hiddenVisibilityRequest != null
    }

    fun plan(input: Input): Plan {
        // Decision: the renderer should execute view mutations, not re-derive the pinyin reveal
        // contract. Keeping this pass plan pure makes "render, sync, clear, or wait" testable.
        val fallbackContentReady = input.previousVisibilityRequest?.contentReady ?: true
        val hiddenRequest = if (!input.nextState.shouldShow) {
            T9CandidateVisibilityPlanner.Request(
                shouldShow = false,
                contentReady = fallbackContentReady,
                preferAboveInputPanel = input.nextState.preferAboveInputPanel
            )
        } else {
            null
        }
        val hiddenAction = hiddenRequest?.let {
            T9CandidateVisibilityPlanner.plan(input.previousVisibilityRequest, it)
        } ?: T9CandidateVisibilityPlanner.Action.NONE
        return Plan(
            pinyinAction = pinyinAction(input),
            fallbackContentReady = fallbackContentReady,
            hiddenVisibilityRequest = hiddenRequest,
            hiddenVisibilityAction = hiddenAction
        )
    }

    fun visibleRequest(
        nextState: T9CandidateRenderState,
        contentReady: Boolean
    ): T9CandidateVisibilityPlanner.Request =
        T9CandidateVisibilityPlanner.Request(
            shouldShow = nextState.shouldShow,
            contentReady = contentReady,
            preferAboveInputPanel = nextState.preferAboveInputPanel
        )

    private fun pinyinAction(input: Input): PinyinAction {
        if (!input.nextState.shouldShow) return PinyinAction.NONE
        val hasPinyinRow = input.nextState.pinyinUseT9 && input.nextState.readingOptions.isNotEmpty()
        val previousPinyinWasReady = input.previousVisibilityRequest?.contentReady == true
        val previousPanelWasShown = input.previousVisibilityRequest?.shouldShow == true
        val shouldClearPinyinRow = !input.nextState.pinyinUseT9 &&
            (
                input.previousState == null ||
                    input.previousState.pinyinUseT9 ||
                    input.previousState.readingOptions.isNotEmpty()
                )
        val shouldEnsurePinyinRow = hasPinyinRow &&
            (
                input.previousState == null ||
                    !previousPanelWasShown ||
                    !input.previousState.pinyinUseT9 ||
                    input.previousState.readingOptions.isEmpty() ||
                    !previousPinyinWasReady
                )
        return when {
            shouldClearPinyinRow -> PinyinAction.CLEAR
            input.patch.pinyin || shouldEnsurePinyinRow -> PinyinAction.RENDER
            input.patch.candidateContent && hasPinyinRow -> PinyinAction.SYNC_LAYOUT
            else -> PinyinAction.NONE
        }
    }
}

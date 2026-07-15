/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.View
import android.widget.TextView
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesOrientation
import org.fcitx.fcitx5.android.input.candidates.floating.PagedCandidatesUi
import org.fcitx.fcitx5.android.input.candidates.floating.T9ShortcutCandidatesUi
import org.fcitx.fcitx5.android.input.preedit.PreeditUi

class T9CandidateSurfaceAndroidAdapter(
    private val preeditUi: PreeditUi,
    private val pinyinRowAdapter: T9PinyinRowAndroidAdapter,
    private val candidatesUi: PagedCandidatesUi,
    private val shortcutCandidatesUi: T9ShortcutCandidatesUi,
    private val candidateStatusView: TextView,
    private val candidateStatusText: (T9CandidateStatus) -> CharSequence,
    private val shortcutCandidateLayout: (FcitxEvent.PagedCandidateEvent.Data) -> T9ShortcutCandidateLayout,
    private val onShortcutCandidateMeasured: (generationId: Long, widthPx: Int?) -> Unit,
    private val setPreferAboveInputPanel: (Boolean) -> Unit,
    private val showWhenPositioned: (contentReady: Boolean) -> Unit,
    private val hideSurfaceImmediately: () -> Unit
) : T9CandidateUiRenderer.Delegate {
    private var lastRenderedFocus = T9CandidateFocus.BOTTOM
    private var activeGenerationId = 0L

    fun beginFrame(generationId: Long) {
        activeGenerationId = generationId
    }

    override fun setPreferAboveInputPanel(preferAboveInputPanel: Boolean) {
        setPreferAboveInputPanel.invoke(preferAboveInputPanel)
    }

    override fun renderPreedit(panel: FcitxEvent.InputPanelEvent.Data, reserveRow: Boolean) {
        preeditUi.update(panel)
        preeditUi.root.visibility = when {
            preeditUi.visible -> View.VISIBLE
            reserveRow -> View.INVISIBLE
            else -> View.GONE
        }
    }

    override fun renderCandidates(
        candidates: FcitxEvent.PagedCandidateEvent.Data,
        orientation: FloatingCandidatesOrientation,
        showShortcutLabels: Boolean,
        shortcutStyle: T9ShortcutCandidateStyle,
        candidateStatus: T9CandidateStatus?
    ) {
        T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderCandidates.visibility") {
            if (candidateStatus != null) {
                candidatesUi.root.visibility = View.GONE
                shortcutCandidatesUi.root.visibility = View.GONE
                candidateStatusView.text = candidateStatusText(candidateStatus)
                candidateStatusView.visibility = View.VISIBLE
            } else if (showShortcutLabels) {
                candidatesUi.root.visibility = View.GONE
                shortcutCandidatesUi.root.visibility = View.VISIBLE
                candidateStatusView.visibility = View.GONE
            } else {
                shortcutCandidatesUi.root.visibility = View.GONE
                candidatesUi.root.visibility = View.VISIBLE
                candidateStatusView.visibility = View.GONE
            }
        }
        if (candidateStatus != null) {
            onShortcutCandidateMeasured(activeGenerationId, null)
            return
        }
        if (showShortcutLabels) {
            T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderCandidates.shortcutLayout") {
                shortcutCandidateLayout.invoke(candidates)
            }.let { layout ->
                T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderCandidates.shortcutUpdate") {
                    shortcutCandidatesUi.update(candidates, layout, shortcutStyle)
                }
                onShortcutCandidateMeasured(
                    activeGenerationId,
                    shortcutCandidatesUi.measuredToolbarWidthPx
                )
            }
            return
        }
        onShortcutCandidateMeasured(activeGenerationId, null)
        T9ResponsivenessTrace.measure("CandidatesView.updateUi.renderCandidates.pagedUpdate") {
            candidatesUi.update(candidates, orientation)
        }
    }

    override fun renderCandidateSelection(
        candidates: FcitxEvent.PagedCandidateEvent.Data
    ): Boolean {
        if (shortcutCandidatesUi.root.visibility != View.VISIBLE) return false
        val rendered = shortcutCandidatesUi.updateSelection(candidates)
        if (rendered) {
            onShortcutCandidateMeasured(
                activeGenerationId,
                shortcutCandidatesUi.measuredToolbarWidthPx
            )
        }
        return rendered
    }

    override fun renderPinyin(readingOptions: List<String>, pinyinUseT9: Boolean): Boolean =
        pinyinRowAdapter.render(readingOptions, pinyinUseT9)

    override fun syncPinyinLayout(): Boolean =
        pinyinRowAdapter.syncVisibleLayout()

    override fun renderFocus(focus: T9CandidateFocus) {
        val topFocused = focus == T9CandidateFocus.TOP
        pinyinRowAdapter.renderFocus(focus, lastRenderedFocus)
        candidatesUi.setHighlightActive(!topFocused)
        shortcutCandidatesUi.setHighlightActive(!topFocused)
        lastRenderedFocus = focus
    }

    override fun showWhenPositioned(contentReady: Boolean) {
        showWhenPositioned.invoke(contentReady)
    }

    override fun hideCandidateUi() {
        hideSurfaceImmediately()
    }

    fun resetFocus() {
        lastRenderedFocus = T9CandidateFocus.BOTTOM
    }

    fun clear(
        inputPanel: FcitxEvent.InputPanelEvent.Data,
        emptyPaged: FcitxEvent.PagedCandidateEvent.Data,
        orientation: FloatingCandidatesOrientation
    ) {
        preeditUi.update(inputPanel)
        preeditUi.root.visibility = View.GONE
        pinyinRowAdapter.clear()
        resetFocus()
        candidatesUi.update(emptyPaged, orientation)
        shortcutCandidatesUi.root.visibility = View.GONE
        candidateStatusView.visibility = View.GONE
    }

    fun cancelPendingPinyinReveal() {
        pinyinRowAdapter.cancelPendingReveal()
    }

}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.View
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
    private val shortcutCandidateLayout: (FcitxEvent.PagedCandidateEvent.Data) -> T9ShortcutCandidateLayout,
    private val setPreferAboveCursorAnchor: (Boolean) -> Unit,
    private val showWhenPositioned: (contentReady: Boolean) -> Unit,
    private val hideSurfaceImmediately: () -> Unit
) : T9CandidateUiRenderer.Delegate {
    private var lastRenderedFocus = T9CandidateFocus.BOTTOM

    override fun setPreferAboveCursorAnchor(preferAboveCursorAnchor: Boolean) {
        setPreferAboveCursorAnchor.invoke(preferAboveCursorAnchor)
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
        showShortcutLabels: Boolean
    ) {
        if (showShortcutLabels) {
            candidatesUi.root.visibility = View.GONE
            shortcutCandidatesUi.root.visibility = View.VISIBLE
            shortcutCandidatesUi.update(candidates, shortcutCandidateLayout.invoke(candidates))
            return
        }
        shortcutCandidatesUi.root.visibility = View.GONE
        candidatesUi.root.visibility = View.VISIBLE
        candidatesUi.update(candidates, orientation)
    }

    override fun renderPinyin(pinyinOptions: List<String>, pinyinUseT9: Boolean): Boolean =
        pinyinRowAdapter.render(pinyinOptions, pinyinUseT9)

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
    }

    fun removePinyinRevealListener() {
        pinyinRowAdapter.removeRevealListener()
    }

}

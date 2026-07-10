/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.t9.SmartEnglishPredictionDictionary
import org.fcitx.fcitx5.android.ui.common.BaseDynamicListUi
import org.fcitx.fcitx5.android.ui.common.OnItemChangedListener
import org.fcitx.fcitx5.android.ui.main.MainViewModel

class SmartEnglishLearnedPredictionsFragment :
    Fragment(),
    OnItemChangedListener<SmartEnglishLearnedPredictionsFragment.LearnedPredictionPair> {

    private val viewModel: MainViewModel by activityViewModels()
    private val dictionary = SmartEnglishPredictionDictionary.Shared
    private var uiInitialized = false

    private val ui: BaseDynamicListUi<LearnedPredictionPair> by lazy {
        object : BaseDynamicListUi<LearnedPredictionPair>(
            requireContext(),
            Mode.FreeAdd(
                hint = getString(R.string.smart_english_learned_prediction_hint),
                converter = { LearnedPredictionPair.parse(it) ?: LearnedPredictionPair.Empty },
                validator = { LearnedPredictionPair.parse(it) != null }
            ),
            initialEntries = dictionary.learnedPairs().toLearnedPredictionPairs()
        ) {
            init {
                addTouchCallback()
                setViewModel(viewModel)
                enableSearch(getString(R.string.search))
            }

            override fun showEntry(x: LearnedPredictionPair): String =
                "${x.previous} -> ${x.next}"
        }.also {
            uiInitialized = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ui.addOnItemChangedListener(this)
        return ui.root
    }

    override fun onItemAdded(idx: Int, item: LearnedPredictionPair) {
        savePairs()
    }

    override fun onItemRemoved(idx: Int, item: LearnedPredictionPair) {
        savePairs()
    }

    override fun onItemRemovedBatch(indexed: List<Pair<Int, LearnedPredictionPair>>) {
        savePairs()
    }

    override fun onItemUpdated(idx: Int, old: LearnedPredictionPair, new: LearnedPredictionPair) {
        savePairs()
    }

    private fun savePairs() {
        dictionary.replaceLearnedPairs(
            ui.entries.groupBy { it.previous }
                .mapValues { (_, pairs) ->
                    pairs.map {
                        SmartEnglishPredictionDictionary.Prediction(it.next, it.score)
                    }
                }
        )
    }

    override fun onStart() {
        super.onStart()
        if (uiInitialized) {
            viewModel.enableToolbarEditButton(ui.entries.isNotEmpty()) {
                ui.enterMultiSelect(requireActivity().onBackPressedDispatcher)
            }
        }
    }

    override fun onStop() {
        viewModel.disableToolbarEditButton()
        if (uiInitialized) {
            ui.exitMultiSelect()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (uiInitialized) {
            ui.removeItemChangedListener()
        }
        super.onDestroy()
    }

    data class LearnedPredictionPair(
        val previous: String,
        val next: String,
        val score: Int = 1
    ) {
        companion object {
            val Empty = LearnedPredictionPair("", "")

            fun parse(raw: String): LearnedPredictionPair? {
                val parts = raw.split("->", limit = 2)
                if (parts.size != 2) return null
                val previous = SmartEnglishPredictionDictionary.normalizePredictionWord(parts[0])
                    ?: return null
                val next = SmartEnglishPredictionDictionary.normalizePredictionWord(parts[1])
                    ?: return null
                if (previous == next) return null
                return LearnedPredictionPair(previous, next)
            }
        }
    }
}

private fun Map<String, List<SmartEnglishPredictionDictionary.Prediction>>.toLearnedPredictionPairs():
    List<SmartEnglishLearnedPredictionsFragment.LearnedPredictionPair> =
    toSortedMap().flatMap { (previous, predictions) ->
        predictions.map { prediction ->
            SmartEnglishLearnedPredictionsFragment.LearnedPredictionPair(
                previous = previous,
                next = prediction.word,
                score = prediction.score
            )
        }
    }

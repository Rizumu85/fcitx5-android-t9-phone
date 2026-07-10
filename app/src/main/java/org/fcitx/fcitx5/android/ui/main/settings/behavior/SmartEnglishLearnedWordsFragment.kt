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
import org.fcitx.fcitx5.android.input.t9.T9EnglishDictionary
import org.fcitx.fcitx5.android.ui.common.BaseDynamicListUi
import org.fcitx.fcitx5.android.ui.common.OnItemChangedListener
import org.fcitx.fcitx5.android.ui.main.MainViewModel

class SmartEnglishLearnedWordsFragment : Fragment(), OnItemChangedListener<String> {

    private val viewModel: MainViewModel by activityViewModels()
    private val dictionary = T9EnglishDictionary.Shared
    private var uiInitialized = false

    private val ui: BaseDynamicListUi<String> by lazy {
        object : BaseDynamicListUi<String>(
            requireContext(),
            Mode.FreeAdd(
                hint = getString(R.string.smart_english_learned_word_hint),
                converter = { T9EnglishDictionary.normalizeLearnedWord(it).orEmpty() },
                validator = { T9EnglishDictionary.normalizeLearnedWord(it) != null }
            ),
            dictionary.learnedWords()
        ) {
            init {
                addTouchCallback()
                setViewModel(viewModel)
                enableSearch(getString(R.string.search))
            }

            override fun showEntry(x: String): String = x
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

    override fun onItemAdded(idx: Int, item: String) {
        saveWords()
    }

    override fun onItemRemoved(idx: Int, item: String) {
        saveWords()
    }

    override fun onItemRemovedBatch(indexed: List<Pair<Int, String>>) {
        saveWords()
    }

    override fun onItemUpdated(idx: Int, old: String, new: String) {
        saveWords()
    }

    private fun saveWords() {
        dictionary.replaceLearnedWords(ui.entries)
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
}

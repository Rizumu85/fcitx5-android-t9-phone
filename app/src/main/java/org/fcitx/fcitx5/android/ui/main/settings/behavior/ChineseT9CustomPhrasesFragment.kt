/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.t9.ChineseT9CustomPhrase
import org.fcitx.fcitx5.android.input.t9.ChineseT9CustomPhraseDictionary
import org.fcitx.fcitx5.android.ui.common.BaseDynamicListUi
import org.fcitx.fcitx5.android.ui.common.OnItemChangedListener
import org.fcitx.fcitx5.android.ui.main.MainViewModel
import org.fcitx.fcitx5.android.utils.materialTextInput
import org.fcitx.fcitx5.android.utils.onPositiveButtonClick
import org.fcitx.fcitx5.android.utils.str
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.verticalLayout
import splitties.views.setPaddingDp

class ChineseT9CustomPhrasesFragment :
    Fragment(),
    OnItemChangedListener<ChineseT9CustomPhrase> {

    private val viewModel: MainViewModel by activityViewModels()
    private val dictionary = ChineseT9CustomPhraseDictionary.Shared
    private var uiInitialized = false

    private val ui: BaseDynamicListUi<ChineseT9CustomPhrase> by lazy {
        object : BaseDynamicListUi<ChineseT9CustomPhrase>(
            requireContext(),
            Mode.FreeAdd(
                hint = getString(R.string.chinese_custom_phrase_hint),
                converter = { ChineseT9CustomPhrase("", "") }
            ),
            dictionary.entries()
        ) {
            init {
                addTouchCallback()
                setViewModel(viewModel)
                enableSearch(getString(R.string.search))
            }

            override fun showEntry(x: ChineseT9CustomPhrase): String =
                "${x.text}  ·  ${x.pinyin.replace('\'', ' ')}"

            override fun showEditDialog(
                title: String,
                entry: ChineseT9CustomPhrase?,
                block: (ChineseT9CustomPhrase) -> Unit
            ) {
                val (textLayout, textField) = materialTextInput {
                    hint = getString(R.string.chinese_custom_phrase_hint)
                }
                textField.apply {
                    isSingleLine = true
                    imeOptions = EditorInfo.IME_ACTION_NEXT
                    inputType = InputType.TYPE_CLASS_TEXT
                }
                val (pinyinLayout, pinyinField) = materialTextInput {
                    hint = getString(R.string.chinese_custom_phrase_pinyin_hint)
                }
                pinyinField.apply {
                    isSingleLine = true
                    imeOptions = EditorInfo.IME_ACTION_DONE
                    inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    filters = arrayOf(
                        InputFilter { source, _, _, _, _, _ ->
                            source.filter { char ->
                                char in 'a'..'z' ||
                                    char in 'A'..'Z' ||
                                    char == '\'' ||
                                    char.isWhitespace()
                            }
                        }
                    )
                }
                entry?.let {
                    textField.setText(it.text)
                    pinyinField.setText(it.pinyin.replace('\'', ' '))
                }
                val content = verticalLayout {
                    setPaddingDp(20, 10, 20, 0)
                    add(textLayout, lParams(matchParent))
                    add(pinyinLayout, lParams(matchParent))
                }
                AlertDialog.Builder(context)
                    .setTitle(title)
                    .setView(content)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                    .onPositiveButtonClick onClick@{
                        val normalized = ChineseT9CustomPhrase.create(
                            rawText = textField.str,
                            rawPinyin = pinyinField.str
                        )
                        if (normalized == null) {
                            if (ChineseT9CustomPhrase.normalizePinyin(pinyinField.str) == null) {
                                pinyinField.error =
                                    getString(R.string.chinese_custom_phrase_pinyin_invalid)
                                pinyinField.requestFocus()
                            } else {
                                textField.error =
                                    getString(R.string.chinese_custom_phrase_text_invalid)
                                textField.requestFocus()
                            }
                            return@onClick false
                        }
                        textField.error = null
                        pinyinField.error = null
                        block(normalized)
                        true
                    }
                    .setCanceledOnTouchOutside(false)
            }
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

    override fun onItemAdded(idx: Int, item: ChineseT9CustomPhrase) = save()

    override fun onItemRemoved(idx: Int, item: ChineseT9CustomPhrase) = save()

    override fun onItemRemovedBatch(indexed: List<Pair<Int, ChineseT9CustomPhrase>>) = save()

    override fun onItemUpdated(
        idx: Int,
        old: ChineseT9CustomPhrase,
        new: ChineseT9CustomPhrase
    ) = save()

    private fun save() {
        dictionary.replaceEntries(ui.entries)
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
        if (uiInitialized) ui.exitMultiSelect()
        super.onStop()
    }

    override fun onDestroy() {
        if (uiInitialized) ui.removeItemChangedListener()
        super.onDestroy()
    }
}

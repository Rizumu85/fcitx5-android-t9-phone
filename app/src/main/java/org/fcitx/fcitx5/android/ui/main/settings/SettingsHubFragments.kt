/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings

import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.navigateWithAnim

abstract class SettingsHubFragment : PaddingPreferenceFragment() {
    protected fun PreferenceCategory.addDestination(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        route: SettingsRoute
    ) {
        addPreference(title, icon = icon) {
            navigateWithAnim(route)
        }
    }

    protected fun createHub(build: PreferenceScreen.() -> Unit) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply(build)
    }
}

class InputOptionsSettingsFragment : SettingsHubFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) = createHub {
        addCategory(R.string.chinese_input) {
            addDestination(
                R.string.chinese_t9_schemes,
                R.drawable.ic_baseline_dialpad_24,
                SettingsRoute.ChineseT9Schemes
            )
        }
        addCategory(R.string.dictionary_and_learning) {
            addDestination(
                R.string.dictionary_management,
                R.drawable.ic_baseline_library_books_24,
                SettingsRoute.DictionaryManagement
            )
        }
    }
}

class KeysAndToolbarSettingsFragment : SettingsHubFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) = createHub {
        addCategory(R.string.keys_and_toolbar) {
            addDestination(
                R.string.virtual_keyboard,
                R.drawable.ic_baseline_keyboard_24,
                SettingsRoute.VirtualKeyboard
            )
        }
    }
}

class AppearanceAndCandidatesSettingsFragment : SettingsHubFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) = createHub {
        addCategory(R.string.appearance_and_candidates) {
            addDestination(R.string.theme, R.drawable.ic_baseline_palette_24, SettingsRoute.Theme)
            addDestination(
                R.string.candidates_window,
                R.drawable.ic_baseline_list_alt_24,
                SettingsRoute.CandidatesWindow
            )
        }
    }
}

class ContentAndToolsSettingsFragment : SettingsHubFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) = createHub {
        addCategory(R.string.content_and_tools) {
            addDestination(R.string.clipboard, R.drawable.ic_clipboard, SettingsRoute.Clipboard)
            addDestination(
                R.string.emoji_and_symbols,
                R.drawable.ic_baseline_emoji_symbols_24,
                SettingsRoute.Symbol
            )
        }
    }
}

class AdvancedSettingsHubFragment : SettingsHubFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) = createHub {
        addCategory(R.string.input_feedback_tuning) {
            addDestination(
                R.string.advanced,
                R.drawable.ic_baseline_tune_24,
                SettingsRoute.Advanced
            )
        }
        addCategory(R.string.extensions_and_fcitx) {
            addDestination(
                R.string.global_options,
                R.drawable.ic_baseline_tune_24,
                SettingsRoute.GlobalConfig
            )
            addDestination(
                R.string.input_methods,
                R.drawable.ic_baseline_language_24,
                SettingsRoute.InputMethodList
            )
            addDestination(
                R.string.addons,
                R.drawable.ic_baseline_extension_24,
                SettingsRoute.AddonList
            )
            addDestination(
                R.string.plugins,
                R.drawable.ic_baseline_android_24,
                SettingsRoute.Plugin
            )
        }
    }
}

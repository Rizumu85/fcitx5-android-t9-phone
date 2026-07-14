/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceCategory
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.navigateWithAnim

class MainFragment : PaddingPreferenceFragment() {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onStart() {
        super.onStart()
        viewModel.enableAboutButton()
    }

    override fun onStop() {
        viewModel.disableAboutButton()
        super.onStop()
    }

    private fun PreferenceCategory.addDestinationPreference(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        route: SettingsRoute
    ) {
        addPreference(title, icon = icon) {
            navigateWithAnim(route)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addCategory(R.string.settings) {
                addDestinationPreference(
                    R.string.input_options,
                    R.drawable.ic_baseline_language_24,
                    SettingsRoute.InputOptions()
                )
                addDestinationPreference(
                    R.string.appearance_and_candidates,
                    R.drawable.ic_baseline_palette_24,
                    SettingsRoute.AppearanceAndCandidates
                )
                addDestinationPreference(
                    R.string.keys_and_toolbar,
                    R.drawable.ic_baseline_keyboard_24,
                    SettingsRoute.KeysAndToolbar
                )
                addDestinationPreference(
                    R.string.clipboard_and_symbols,
                    R.drawable.ic_baseline_content_paste_24,
                    SettingsRoute.ClipboardAndSymbols
                )
                addDestinationPreference(
                    R.string.advanced,
                    R.drawable.ic_baseline_more_horiz_24,
                    SettingsRoute.AdvancedHub
                )
            }
            addCategory(R.string.app_information) {
                addDestinationPreference(
                    R.string.about,
                    R.drawable.ic_baseline_info_24,
                    SettingsRoute.About
                )
            }
        }
    }
}

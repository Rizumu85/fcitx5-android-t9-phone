/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings

import android.os.Bundle
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.navigateWithAnim

class DictionaryManagementFragment : PaddingPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addPreference(
                R.string.smart_english_learned_words,
                R.string.smart_english_learned_words_summary
            ) {
                navigateWithAnim(SettingsRoute.SmartEnglishLearnedWords)
            }
            addPreference(
                R.string.smart_english_learned_predictions,
                R.string.smart_english_learned_predictions_summary
            ) {
                navigateWithAnim(SettingsRoute.SmartEnglishLearnedPredictions)
            }
        }
    }
}

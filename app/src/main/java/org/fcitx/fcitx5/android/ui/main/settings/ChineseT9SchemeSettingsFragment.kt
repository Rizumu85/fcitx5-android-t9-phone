/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings

import android.widget.Toast
import androidx.preference.PreferenceScreen
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.modified.MySwitchPreference

class ChineseT9SchemeSettingsFragment :
    ManagedPreferenceFragment(AppPrefs.getInstance().chineseT9) {

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val prefs = AppPrefs.getInstance().chineseT9
        val switches = listOfNotNull(
            screen.findPreference<MySwitchPreference>(prefs.pinyin.key),
            screen.findPreference<MySwitchPreference>(prefs.stroke.key),
            screen.findPreference<MySwitchPreference>(prefs.zhuyin.key)
        )
        switches.forEach { preference ->
            preference.setOnPreferenceChangeListener { _, newValue ->
                val disablingLast = newValue == false &&
                    switches.none { other -> other !== preference && other.isChecked }
                if (disablingLast) {
                    Toast.makeText(
                        requireContext(),
                        R.string.chinese_t9_at_least_one,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                !disablingLast
            }
        }
    }
}

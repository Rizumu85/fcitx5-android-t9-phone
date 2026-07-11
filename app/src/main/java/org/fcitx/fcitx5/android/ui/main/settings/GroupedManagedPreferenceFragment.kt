/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings

import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceVisibilityEvaluator
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment

/**
 * Renders product-oriented settings sections without forcing the preference storage model to
 * mirror the navigation hierarchy. A setting has one owner, while screens choose where to expose
 * that setting to the user.
 */
abstract class GroupedManagedPreferenceFragment : PaddingPreferenceFragment() {
    protected data class Group(
        @StringRes val title: Int,
        val provider: ManagedPreferenceProvider,
        val keys: Set<String>
    )

    protected abstract fun groups(): List<Group>

    private val evaluators = mutableListOf<ManagedPreferenceVisibilityEvaluator>()

    protected open fun onGroupedPreferenceUiCreated(screen: PreferenceScreen) = Unit

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val groups = groups()
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).also { screen ->
            groups.groupBy(Group::title).forEach { (title, sectionGroups) ->
                val category = PreferenceCategory(screen.context).apply {
                    setTitle(title)
                    isIconSpaceReserved = false
                }
                screen.addPreference(category)
                sectionGroups.forEach { group ->
                    group.provider.managedPreferencesUi
                        .asSequence()
                        .filter { it.key in group.keys }
                        .forEach { ui ->
                            category.addPreference(ui.createUi(screen.context).apply {
                                isEnabled = ui.isEnabled()
                            })
                        }
                    evaluators += ManagedPreferenceVisibilityEvaluator(group.provider) { changes ->
                        changes.forEach { (key, enabled) ->
                            if (key in group.keys) {
                                screen.findPreference<Preference>(key)?.isEnabled = enabled
                            }
                        }
                    }.also { it.evaluateVisibility() }
                }
            }
            onGroupedPreferenceUiCreated(screen)
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            AppPrefs.getInstance().syncToDeviceEncryptedStorage()
        }
        super.onStop()
    }

    override fun onDestroy() {
        evaluators.forEach(ManagedPreferenceVisibilityEvaluator::destroy)
        evaluators.clear()
        super.onDestroy()
    }
}

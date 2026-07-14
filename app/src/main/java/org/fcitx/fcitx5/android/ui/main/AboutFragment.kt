/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.update.UpdateCheckUi
import org.fcitx.fcitx5.android.update.UpdateChecker
import org.fcitx.fcitx5.android.update.InstalledUpdateVersionsResolver
import org.fcitx.fcitx5.android.utils.Const
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.formatDateTime
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import kotlinx.coroutines.launch

class AboutFragment : PaddingPreferenceFragment() {

    private lateinit var checkUpdatePreference: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addPreference(R.string.privacy_policy) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Const.privacyPolicyUrl)))
            }
            addPreference(
                R.string.open_source_licenses,
                R.string.licenses_of_third_party_libraries
            ) {
                navigateWithAnim(SettingsRoute.License)
            }
            addPreference(R.string.source_code, R.string.github_repo) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Const.githubRepo)))
            }
            addPreference(R.string.license, Const.licenseSpdxId) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Const.licenseUrl)))
            }
            addCategory(R.string.version) {
                isIconSpaceReserved = false
                addPreference(R.string.current_version, Const.versionName)
                checkUpdatePreference = Preference(context).apply {
                    isIconSpaceReserved = false
                    setTitle(R.string.check_for_updates)
                    setSummary(R.string.check_for_updates_summary)
                    setOnPreferenceClickListener {
                        checkForUpdates()
                        true
                    }
                }
                addPreference(checkUpdatePreference)
                addPreference(R.string.build_git_hash, BuildConfig.BUILD_GIT_HASH) {
                    val commit = BuildConfig.BUILD_GIT_HASH.substringBefore('-')
                    val uri = Uri.parse("${Const.githubRepo}/commit/${commit}")
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                addPreference(R.string.build_time, formatDateTime(BuildConfig.BUILD_TIME))
            }
        }
    }

    private fun checkForUpdates() {
        checkUpdatePreference.isEnabled = false
        checkUpdatePreference.setSummary(R.string.checking_for_updates)
        lifecycleScope.launch {
            val installedVersions = InstalledUpdateVersionsResolver.resolve(requireContext())
            when (val result = UpdateChecker(
                installedVersions,
                InstalledUpdateVersionsResolver.supportedComponents()
            ).check()) {
                is UpdateChecker.Result.Available ->
                    UpdateCheckUi.showAvailable(requireContext(), result)
                UpdateChecker.Result.UpToDate ->
                    UpdateCheckUi.showUpToDate(requireContext())
                is UpdateChecker.Result.Failed ->
                    UpdateCheckUi.showFailure(requireContext())
            }
            checkUpdatePreference.isEnabled = true
            checkUpdatePreference.setSummary(R.string.check_for_updates_summary)
        }
    }
}

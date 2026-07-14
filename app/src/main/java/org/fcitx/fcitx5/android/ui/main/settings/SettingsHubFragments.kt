/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.handwriting.MlKitHandwritingModelManager
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.modified.MySwitchPreference
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.buildDocumentsProviderIntent
import org.fcitx.fcitx5.android.utils.lazyRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import org.fcitx.fcitx5.android.utils.toast

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

class InputOptionsSettingsFragment : GroupedManagedPreferenceFragment() {
    private enum class HandwritingModelUiState { CHECKING, AVAILABLE, MISSING, DOWNLOADING, FAILED }

    private val prefs = AppPrefs.getInstance()
    private val route by lazyRoute<SettingsRoute.InputOptions>()
    private val handwritingModelManager = MlKitHandwritingModelManager()
    private var handwritingModelPreference: Preference? = null
    private var handwritingModelJob: Job? = null
    private var handwritingModelUiState = HandwritingModelUiState.CHECKING
    private var handwritingDownloadRequestConsumed = false

    override fun groups() = listOf(
        Group(
            R.string.chinese_input,
            prefs.chineseT9,
            setOf(
                prefs.chineseT9.pinyin.key,
                prefs.chineseT9.pinyinOutputScript.key,
                prefs.chineseT9.stroke.key,
                prefs.chineseT9.strokeOutputScript.key,
                prefs.chineseT9.zhuyin.key,
                prefs.chineseT9.zhuyinOutputScript.key
            )
        ),
        Group(
            R.string.smart_english_t9,
            prefs.keyboard,
            setOf(prefs.keyboard.smartEnglishT9.key)
        ),
        Group(R.string.handwriting_input, prefs.keyboard, emptySet()),
        Group(
            R.string.voice_input,
            prefs.keyboard,
            setOf(
                prefs.keyboard.preferredVoiceInput.key,
                prefs.keyboard.longPressZeroVoiceInput.key
            )
        )
    )

    override fun onGroupedPreferenceUiCreated(screen: PreferenceScreen) {
        val schemeSwitches = listOfNotNull(
            screen.findPreference<MySwitchPreference>(prefs.chineseT9.pinyin.key),
            screen.findPreference<MySwitchPreference>(prefs.chineseT9.stroke.key),
            screen.findPreference<MySwitchPreference>(prefs.chineseT9.zhuyin.key)
        )
        schemeSwitches.forEach { preference ->
            preference.setOnPreferenceChangeListener { _, newValue ->
                val allowed = newValue != false || schemeSwitches.any { other ->
                    other !== preference && other.isChecked
                }
                if (!allowed) {
                    Toast.makeText(
                        requireContext(),
                        R.string.chinese_t9_at_least_one,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                allowed
            }
        }
        screen.findPreference<PreferenceCategory>(
            groupKey(R.string.smart_english_t9)
        )?.apply {
            addPreference(
                androidx.preference.Preference(screen.context).apply {
                    setTitle(R.string.smart_english_learned_words)
                    setSummary(R.string.smart_english_learned_words_summary)
                    isIconSpaceReserved = false
                    setOnPreferenceClickListener {
                        navigateWithAnim(SettingsRoute.SmartEnglishLearnedWords)
                        true
                    }
                }
            )
            addPreference(
                androidx.preference.Preference(screen.context).apply {
                    setTitle(R.string.smart_english_learned_predictions)
                    setSummary(R.string.smart_english_learned_predictions_summary)
                    isIconSpaceReserved = false
                    setOnPreferenceClickListener {
                        navigateWithAnim(SettingsRoute.SmartEnglishLearnedPredictions)
                        true
                    }
                }
            )
        }
        screen.findPreference<PreferenceCategory>(
            groupKey(R.string.handwriting_input)
        )?.apply {
            addPreference(
                Preference(screen.context).apply {
                    key = HandwritingModelPreferenceKey
                    setTitle(R.string.handwriting_enhanced_model)
                    isIconSpaceReserved = false
                    setOnPreferenceClickListener {
                        if (
                            handwritingModelJob?.isActive != true &&
                            (handwritingModelUiState == HandwritingModelUiState.MISSING ||
                                handwritingModelUiState == HandwritingModelUiState.FAILED)
                        ) {
                            downloadHandwritingModel()
                        }
                        true
                    }
                    handwritingModelPreference = this
                }
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (route.requestHandwritingModelDownload) {
            listView.post { scrollToPreference(HandwritingModelPreferenceKey) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHandwritingModelState()
    }

    private fun refreshHandwritingModelState() {
        if (handwritingModelPreference == null || handwritingModelJob?.isActive == true) return
        renderHandwritingModelState(HandwritingModelUiState.CHECKING)
        handwritingModelJob = lifecycleScope.launch {
            val state = runCatching { handwritingModelManager.isDownloaded() }
                .fold(
                    onSuccess = { downloaded ->
                        if (downloaded) HandwritingModelUiState.AVAILABLE
                        else HandwritingModelUiState.MISSING
                    },
                    onFailure = { HandwritingModelUiState.FAILED }
                )
            renderHandwritingModelState(state)
            if (
                route.requestHandwritingModelDownload &&
                !handwritingDownloadRequestConsumed &&
                state == HandwritingModelUiState.MISSING
            ) {
                handwritingDownloadRequestConsumed = true
                downloadHandwritingModel()
            }
        }
    }

    private fun downloadHandwritingModel() {
        renderHandwritingModelState(HandwritingModelUiState.DOWNLOADING)
        handwritingModelJob = lifecycleScope.launch {
            val state = runCatching { handwritingModelManager.download() }
                .fold(
                    onSuccess = { HandwritingModelUiState.AVAILABLE },
                    onFailure = { HandwritingModelUiState.FAILED }
                )
            renderHandwritingModelState(state)
        }
    }

    private fun renderHandwritingModelState(state: HandwritingModelUiState) {
        handwritingModelUiState = state
        handwritingModelPreference?.apply {
            summary = getString(
                when (state) {
                    HandwritingModelUiState.CHECKING -> R.string.handwriting_model_checking
                    HandwritingModelUiState.AVAILABLE -> R.string.handwriting_model_available
                    HandwritingModelUiState.MISSING -> R.string.handwriting_model_missing
                    HandwritingModelUiState.DOWNLOADING -> R.string.handwriting_model_downloading
                    HandwritingModelUiState.FAILED -> R.string.handwriting_model_failed
                }
            )
            isEnabled = state != HandwritingModelUiState.CHECKING &&
                state != HandwritingModelUiState.DOWNLOADING
        }
    }

    private companion object {
        const val HandwritingModelPreferenceKey = "handwriting_enhanced_model"
    }
}

class ClipboardAndSymbolsSettingsFragment : GroupedManagedPreferenceFragment() {
    private val prefs = AppPrefs.getInstance()

    override fun groups() = listOf(
        Group(
            R.string.clipboard,
            prefs.clipboard,
            setOf(
                prefs.clipboard.clipboardListening.key,
                prefs.clipboard.clipboardHistoryLimit.key,
                prefs.clipboard.clipboardRetentionDays.key,
                prefs.clipboard.clipboardMaskSensitive.key
            )
        ),
        Group(
            R.string.emoji_and_symbols,
            prefs.symbols,
            setOf(
                prefs.symbols.hideUnsupportedEmojis.key,
                prefs.symbols.defaultEmojiSkinTone.key
            )
        )
    )

}

class AppearanceAndCandidatesSettingsFragment : GroupedManagedPreferenceFragment() {
    private val prefs = AppPrefs.getInstance()

    override fun groups() = listOf(
        Group(
            R.string.appearance,
            prefs.keyboard,
            setOf(prefs.keyboard.inputUiFont.key)
        ),
        Group(
            R.string.candidates_window,
            prefs.candidates,
            setOf(
                prefs.candidates.fontSize.key,
                prefs.candidates.t9HanziCharacterBudget.key
            )
        )
    )

    override fun onGroupedPreferenceUiCreated(screen: PreferenceScreen) {
        val appearance = screen.findPreference<PreferenceCategory>(groupKey(R.string.appearance))
            ?: return
        appearance.addPreference(
            androidx.preference.Preference(screen.context).apply {
                key = "appearance_theme"
                order = -2
                setTitle(R.string.theme)
                setIcon(R.drawable.ic_baseline_palette_24)
                isSingleLineTitle = false
                setOnPreferenceClickListener {
                    navigateWithAnim(SettingsRoute.Theme)
                    true
                }
            }
        )
        appearance.addPreference(
            androidx.preference.Preference(screen.context).apply {
                key = "open_fonts_folder"
                order = 2
                setTitle(R.string.open_fonts_folder)
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    try {
                        screen.context.startActivity(buildDocumentsProviderIntent("fonts"))
                    } catch (e: Exception) {
                        screen.context.toast(e)
                    }
                    true
                }
            }
        )
    }
}

class AdvancedSettingsHubFragment : GroupedManagedPreferenceFragment() {
    private val prefs = AppPrefs.getInstance()

    private fun PreferenceCategory.addDestination(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        route: SettingsRoute
    ) {
        addPreference(title, icon = icon) {
            navigateWithAnim(route)
        }
    }

    override fun groups() = listOf(
        Group(
            R.string.input_feedback_tuning,
            prefs.keyboard,
            setOf(
                prefs.keyboard.hapticOnKeyUp.key,
                prefs.keyboard.hapticOnRepeat.key,
                prefs.keyboard.buttonPressVibrationMilliseconds.key,
                prefs.keyboard.buttonLongPressVibrationMilliseconds.key,
                prefs.keyboard.buttonPressVibrationAmplitude.key,
                prefs.keyboard.buttonLongPressVibrationAmplitude.key
            )
        ),
        Group(
            R.string.diagnostics_and_compatibility,
            prefs.advanced,
            prefs.advanced.managedPreferences.keys
        )
    )

    override fun onGroupedPreferenceUiCreated(screen: PreferenceScreen) {
        val extensionsCategory = PreferenceCategory(screen.context).apply {
            setTitle(R.string.extensions_and_fcitx)
            isIconSpaceReserved = false
        }
        screen.addPreference(extensionsCategory)
        extensionsCategory.apply {
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
        screen.findPreference<PreferenceCategory>(
            groupKey(R.string.diagnostics_and_compatibility)
        )?.apply {
            addDestination(
                R.string.repair_and_recovery,
                R.drawable.ic_baseline_settings_backup_restore_24,
                SettingsRoute.Advanced
            )
        }
    }
}

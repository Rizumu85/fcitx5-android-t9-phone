/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.prefs

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.InputFeedbacks.InputFeedbackMode
import org.fcitx.fcitx5.android.input.InputUiFont
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.EmojiModifier
import org.fcitx.fcitx5.android.input.t9.ChineseT9Scheme
import org.fcitx.fcitx5.android.input.t9.ChineseT9OutputScript
import org.fcitx.fcitx5.android.input.t9.T9IdleLongZeroBehavior
import org.fcitx.fcitx5.android.utils.DeviceUtil
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.vibrator

class AppPrefs(private val sharedPreferences: SharedPreferences) {

    inner class Internal : ManagedPreferenceInternal(sharedPreferences) {
        val firstRun = bool("first_run", true)
        val lastSymbolLayout = string("last_symbol_layout", PickerWindow.Key.Symbol.name)
        val lastPickerType = string("last_picker_type", PickerWindow.Key.Emoji.name)
        val verboseLog = bool("verbose_log", false)
        val pid = int("pid", 0)
        val editorInfoInspector = bool("editor_info_inspector", false)
        val t9ResponsivenessTrace = bool("t9_responsiveness_trace", false)
        val needNotifications = bool("need_notifications", true)
    }

    inner class Advanced : ManagedPreferenceCategory(R.string.advanced, sharedPreferences) {
        val ignoreSystemCursor = switch(R.string.ignore_sys_cursor, "ignore_system_cursor", false)
        val hideKeyConfig = switch(R.string.hide_key_config, "hide_key_config", true)
        val disableAnimation = switch(R.string.disable_animation, "disable_animation", false)
        val vivoKeypressWorkaround = switch(
            R.string.vivo_keypress_workaround,
            "vivo_keypress_workaround",
            // there's some feedback that this workaround is no longer necessary on Origin OS 4, which based on Android 14
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && DeviceUtil.isVivoOriginOS
        )
        val ignoreSystemWindowInsets = switch(
            R.string.ignore_system_window_insets, "ignore_system_window_insets", false
        )
    }

    inner class Keyboard : ManagedPreferenceCategory(R.string.virtual_keyboard, sharedPreferences) {
        val hapticOnKeyPress =
            enumList(
                R.string.button_haptic_feedback,
                "haptic_on_keypress",
                InputFeedbackMode.FollowingSystem
            )
        val hapticOnKeyUp = switch(
            R.string.button_up_haptic_feedback,
            "haptic_on_keyup",
            false
        ) { hapticOnKeyPress.getValue() != InputFeedbackMode.Disabled }
        val hapticOnRepeat = switch(R.string.haptic_on_repeat, "haptic_on_repeat", false)

        val buttonPressVibrationMilliseconds: ManagedPreference.PInt
        val buttonLongPressVibrationMilliseconds: ManagedPreference.PInt

        init {
            val (primary, secondary) = twinInt(
                R.string.button_vibration_milliseconds,
                R.string.button_press,
                "button_vibration_press_milliseconds",
                0,
                R.string.button_long_press,
                "button_vibration_long_press_milliseconds",
                0,
                0,
                100,
                "ms",
                defaultLabel = R.string.system_default
            ) { hapticOnKeyPress.getValue() != InputFeedbackMode.Disabled }
            buttonPressVibrationMilliseconds = primary
            buttonLongPressVibrationMilliseconds = secondary
        }

        val buttonPressVibrationAmplitude: ManagedPreference.PInt
        val buttonLongPressVibrationAmplitude: ManagedPreference.PInt

        init {
            val (primary, secondary) = twinInt(
                R.string.button_vibration_amplitude,
                R.string.button_press,
                "button_vibration_press_amplitude",
                0,
                R.string.button_long_press,
                "button_vibration_long_press_amplitude",
                0,
                0,
                255,
                defaultLabel = R.string.system_default
            ) {
                (hapticOnKeyPress.getValue() != InputFeedbackMode.Disabled)
                        // hide this if using default duration
                        && (buttonPressVibrationMilliseconds.getValue() != 0 || buttonLongPressVibrationMilliseconds.getValue() != 0)
                        && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appContext.vibrator.hasAmplitudeControl())
            }
            buttonPressVibrationAmplitude = primary
            buttonLongPressVibrationAmplitude = secondary
        }

        val soundOnKeyPress = enumList(
            R.string.button_sound,
            "sound_on_keypress",
            InputFeedbackMode.FollowingSystem
        )
        val soundOnKeyPressVolume = int(
            R.string.button_sound_volume,
            "button_sound_volume",
            0,
            0,
            100,
            "%",
            defaultLabel = R.string.system_default
        ) {
            soundOnKeyPress.getValue() != InputFeedbackMode.Disabled
        }
        val physicalKeySound = switch(
            R.string.physical_key_sound,
            "physical_key_sound",
            true
        ) {
            soundOnKeyPress.getValue() != InputFeedbackMode.Disabled
        }
        val toolbarNumRowOnPassword =
            switch(R.string.toolbar_num_row_on_password, "toolbar_num_row_on_password", true)
        val passwordInputPreview =
            switch(R.string.password_input_preview, "password_input_preview", true)
        val inputUiFont = dynamicStringList(
            R.string.input_ui_font_family,
            "input_ui_font",
            InputUiFont.SystemDefaultValue,
            InputUiFont::customFontPreferenceEntries,
            R.string.input_ui_font_summary
        )

        private fun toolbarButton(
            key: String,
            defaultValue: Boolean = true
        ) = ManagedPreference.PBool(sharedPreferences, key, defaultValue).also { it.register() }

        // Toolbar membership is edited as one product setting, while separate booleans let the
        // live IME update one slot without parsing or rewriting a serialized collection.
        val showVoiceInputButton = toolbarButton("show_voice_input_button")
        val showUndoButton = toolbarButton("show_toolbar_undo_button")
        val showRedoButton = toolbarButton("show_toolbar_redo_button")
        val showTextEditingButton = toolbarButton("show_toolbar_text_editing_button")
        val showClipboardButton = toolbarButton("show_toolbar_clipboard_button")
        val showHideKeyboardButton = toolbarButton("show_toolbar_hide_keyboard_button")
        val toolbarButtonPreferences = listOf(
            showVoiceInputButton,
            showUndoButton,
            showRedoButton,
            showTextEditingButton,
            showClipboardButton,
            showHideKeyboardButton
        )

        val idleLongZeroBehavior = enumList(
            R.string.t9_idle_long_zero_behavior,
            "t9_idle_long_zero_behavior",
            T9IdleLongZeroBehavior.LiteralZero
        )
        val preferredVoiceInput = voiceInputPreference(
            R.string.preferred_voice_input, "preferred_voice_input", ""
        ) {
            showVoiceInputButton.getValue() ||
                idleLongZeroBehavior.getValue() == T9IdleLongZeroBehavior.VoiceInput
        }

        val longPressDelay = int(
            R.string.keyboard_long_press_delay,
            "keyboard_long_press_delay",
            300,
            100,
            700,
            "ms",
            10
        )
        val smartEnglishT9 =
            switch(
                R.string.smart_english_t9,
                "smart_english_t9",
                BuildConfig.PERFORMANCE_HARNESS
            )

        val t9KeyboardHeightPercent: ManagedPreference.PInt
        val t9KeyboardHeightPercentLandscape: ManagedPreference.PInt

        init {
            val (primary, secondary) = twinInt(
                R.string.t9_keyboard_height,
                R.string.portrait,
                "t9_keyboard_height_percent",
                10,
                R.string.landscape,
                "t9_keyboard_height_percent_landscape",
                15,
                5,
                50,
                "%"
            )
            t9KeyboardHeightPercent = primary
            t9KeyboardHeightPercentLandscape = secondary
        }

    }

    inner class Candidates :
        ManagedPreferenceCategory(R.string.candidates_window, sharedPreferences) {
        val fontSize =
            int(R.string.candidates_font_size, "candidates_window_font_size", 18, 4, 64, "sp")

        val t9HanziCharacterBudget =
            int(R.string.candidates_t9_hanzi_character_budget, "candidates_t9_hanzi_character_budget", 10, 4, 24)
    }

    inner class Clipboard : ManagedPreferenceCategory(R.string.clipboard, sharedPreferences) {
        val clipboardListening = switch(R.string.clipboard_listening, "clipboard_enable", true)
        val clipboardHistoryLimit = int(
            R.string.clipboard_limit,
            "clipboard_limit",
            10,
        ) { clipboardListening.getValue() }
        val clipboardRetentionDays = int(
            R.string.clipboard_retention_days,
            "clipboard_retention_days",
            30,
            -1,
            365,
            "d"
        ) { clipboardListening.getValue() }
        val clipboardMaskSensitive = switch(
            R.string.clipboard_mask_sensitive, "clipboard_mask_sensitive", true
        ) { clipboardListening.getValue() }
    }

    inner class Symbols : ManagedPreferenceCategory(R.string.emoji_and_symbols, sharedPreferences) {
        val hideUnsupportedEmojis = switch(
            R.string.hide_unsupported_emojis,
            "hide_unsupported_emojis",
            true
        )

        val defaultEmojiSkinTone = enumList(
            R.string.default_emoji_skin_tone,
            "default_emoji_skin_tone",
            EmojiModifier.SkinTone.Default,
        )
    }

    inner class ChineseT9 : ManagedPreferenceCategory(R.string.chinese_t9_schemes, sharedPreferences) {
        val pinyin = switch(R.string.chinese_t9_pinyin, "chinese_t9_pinyin_enabled", true)
        val pinyinOutputScript = enumList(
            R.string.chinese_t9_pinyin_output_script,
            "chinese_t9_pinyin_output_script",
            ChineseT9OutputScript.Simplified
        ) { pinyin.getValue() }
        // Performance variants enable every scheme by default so profile collection needs no
        // exported runtime control hook; production BuildConfig folds these defaults to false.
        val stroke = switch(
            R.string.chinese_t9_stroke,
            "chinese_t9_stroke_enabled",
            BuildConfig.PERFORMANCE_HARNESS
        )
        val strokeOutputScript = enumList(
            R.string.chinese_t9_stroke_output_script,
            "chinese_t9_stroke_output_script",
            ChineseT9OutputScript.Simplified
        ) { stroke.getValue() }
        val zhuyin = switch(
            R.string.chinese_t9_zhuyin,
            "chinese_t9_zhuyin_enabled",
            BuildConfig.PERFORMANCE_HARNESS
        )
        val zhuyinOutputScript = enumList(
            R.string.chinese_t9_zhuyin_output_script,
            "chinese_t9_zhuyin_output_script",
            ChineseT9OutputScript.Simplified
        ) { zhuyin.getValue() }

        fun enabledSchemes(): List<ChineseT9Scheme> = buildList {
            if (pinyin.getValue()) add(ChineseT9Scheme.PINYIN)
            if (stroke.getValue()) add(ChineseT9Scheme.STROKE)
            if (zhuyin.getValue()) add(ChineseT9Scheme.ZHUYIN)
        }

        fun outputScriptPreference(
            scheme: ChineseT9Scheme
        ): ManagedPreference.PStringLike<ChineseT9OutputScript> = when (scheme) {
            ChineseT9Scheme.PINYIN -> pinyinOutputScript
            ChineseT9Scheme.STROKE -> strokeOutputScript
            ChineseT9Scheme.ZHUYIN -> zhuyinOutputScript
        }

        fun outputScript(scheme: ChineseT9Scheme): ChineseT9OutputScript =
            outputScriptPreference(scheme).getValue()
    }

    private val providers = mutableListOf<ManagedPreferenceProvider>()

    fun <T : ManagedPreferenceProvider> registerProvider(
        providerF: (SharedPreferences) -> T
    ): T {
        val provider = providerF(sharedPreferences)
        providers.add(provider)
        return provider
    }

    private fun <T : ManagedPreferenceProvider> T.register() = this.apply {
        registerProvider { this }
    }

    val internal = Internal().register()
    val chineseT9 = ChineseT9().register()
    val keyboard = Keyboard().register()
    val candidates = Candidates().register()
    val clipboard = Clipboard().register()
    val symbols = Symbols().register()
    val advanced = Advanced().register()

    @Keep
    private val onSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null) return@OnSharedPreferenceChangeListener
            providers.forEach {
                it.fireChange(key)
            }
        }

    @RequiresApi(Build.VERSION_CODES.N)
    fun syncToDeviceEncryptedStorage() {
        val ctx = appContext.createDeviceProtectedStorageContext()
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit {
            listOf(
                internal.verboseLog,
                internal.editorInfoInspector,
                advanced.ignoreSystemCursor,
                advanced.disableAnimation,
                advanced.vivoKeypressWorkaround
            ).forEach {
                it.putValueTo(this@edit)
            }
            listOf(
                // Chinese scheme and script defaults must survive Direct Boot because the IME can
                // be selected before credential-protected preferences become available.
                chineseT9,
                keyboard,
                candidates,
                clipboard
            ).forEach { category ->
                category.managedPreferences.forEach {
                    it.value.putValueTo(this@edit)
                }
            }
        }
    }

    companion object {
        private var instance: AppPrefs? = null

        /**
         * MUST call before use
         */
        fun init(sharedPreferences: SharedPreferences) {
            if (instance != null)
                return
            instance = AppPrefs(sharedPreferences)
            sharedPreferences.registerOnSharedPreferenceChangeListener(getInstance().onSharedPreferenceChangeListener)
        }

        fun getInstance() = instance!!
    }
}

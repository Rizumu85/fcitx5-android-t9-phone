/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.getSystemSettings
import org.fcitx.fcitx5.android.utils.vibrator

object InputFeedbacks {

    enum class InputFeedbackMode(override val stringRes: Int) : ManagedPreferenceEnum {
        FollowingSystem(R.string.following_system_settings),
        Enabled(R.string.enabled),
        Disabled(R.string.disabled);
    }

    private var systemSoundEffects = false
    private var systemHapticFeedback = false

    fun syncSystemPrefs() {
        systemSoundEffects = getSystemSettings<Int>(Settings.System.SOUND_EFFECTS_ENABLED) == 1
        // it says "Replaced by using android.os.VibrationAttributes.USAGE_TOUCH"
        // but gives no clue about how to use it, and this one still works
        @Suppress("DEPRECATION")
        systemHapticFeedback = getSystemSettings<Int>(Settings.System.HAPTIC_FEEDBACK_ENABLED) == 1
    }

    private val keyboardPrefs = AppPrefs.getInstance().keyboard

    @Volatile
    private var soundOnKeyPress = keyboardPrefs.soundOnKeyPress.getValue()

    @Volatile
    private var soundOnKeyPressVolume = keyboardPrefs.soundOnKeyPressVolume.getValue()

    @Volatile
    private var hapticOnKeyPress = keyboardPrefs.hapticOnKeyPress.getValue()

    @Volatile
    private var hapticOnKeyUp = keyboardPrefs.hapticOnKeyUp.getValue()

    @Volatile
    private var buttonPressVibrationMilliseconds =
        keyboardPrefs.buttonPressVibrationMilliseconds.getValue()

    @Volatile
    private var buttonLongPressVibrationMilliseconds =
        keyboardPrefs.buttonLongPressVibrationMilliseconds.getValue()

    @Volatile
    private var buttonPressVibrationAmplitude =
        keyboardPrefs.buttonPressVibrationAmplitude.getValue()

    @Volatile
    private var buttonLongPressVibrationAmplitude =
        keyboardPrefs.buttonLongPressVibrationAmplitude.getValue()

    private val soundOnKeyPressChangeListener =
        ManagedPreference.OnChangeListener<InputFeedbackMode> { _, value ->
            soundOnKeyPress = value
        }
    private val soundOnKeyPressVolumeChangeListener =
        ManagedPreference.OnChangeListener<Int> { _, value ->
            soundOnKeyPressVolume = value
        }
    private val hapticOnKeyPressChangeListener =
        ManagedPreference.OnChangeListener<InputFeedbackMode> { _, value ->
            hapticOnKeyPress = value
        }
    private val hapticOnKeyUpChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            hapticOnKeyUp = value
        }
    private val buttonPressVibrationMillisecondsChangeListener =
        ManagedPreference.OnChangeListener<Int> { _, value ->
            buttonPressVibrationMilliseconds = value
        }
    private val buttonLongPressVibrationMillisecondsChangeListener =
        ManagedPreference.OnChangeListener<Int> { _, value ->
            buttonLongPressVibrationMilliseconds = value
        }
    private val buttonPressVibrationAmplitudeChangeListener =
        ManagedPreference.OnChangeListener<Int> { _, value ->
            buttonPressVibrationAmplitude = value
        }
    private val buttonLongPressVibrationAmplitudeChangeListener =
        ManagedPreference.OnChangeListener<Int> { _, value ->
            buttonLongPressVibrationAmplitude = value
        }

    init {
        keyboardPrefs.soundOnKeyPress.registerOnChangeListener(soundOnKeyPressChangeListener)
        keyboardPrefs.soundOnKeyPressVolume.registerOnChangeListener(
            soundOnKeyPressVolumeChangeListener
        )
        keyboardPrefs.hapticOnKeyPress.registerOnChangeListener(hapticOnKeyPressChangeListener)
        keyboardPrefs.hapticOnKeyUp.registerOnChangeListener(hapticOnKeyUpChangeListener)
        keyboardPrefs.buttonPressVibrationMilliseconds.registerOnChangeListener(
            buttonPressVibrationMillisecondsChangeListener
        )
        keyboardPrefs.buttonLongPressVibrationMilliseconds.registerOnChangeListener(
            buttonLongPressVibrationMillisecondsChangeListener
        )
        keyboardPrefs.buttonPressVibrationAmplitude.registerOnChangeListener(
            buttonPressVibrationAmplitudeChangeListener
        )
        keyboardPrefs.buttonLongPressVibrationAmplitude.registerOnChangeListener(
            buttonLongPressVibrationAmplitudeChangeListener
        )
    }

    private val vibrator = appContext.vibrator

    private val hasAmplitudeControl =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && vibrator.hasAmplitudeControl()

    fun hapticFeedback(view: View, longPress: Boolean = false, keyUp: Boolean = false) {
        when (hapticOnKeyPress) {
            InputFeedbackMode.Enabled -> {}
            InputFeedbackMode.Disabled -> return
            InputFeedbackMode.FollowingSystem -> if (!systemHapticFeedback) return
        }
        if (keyUp && !hapticOnKeyUp) return
        val duration: Long
        val amplitude: Int
        val hfc: Int
        if (longPress) {
            duration = buttonLongPressVibrationMilliseconds.toLong()
            amplitude = buttonLongPressVibrationAmplitude
            hfc = HapticFeedbackConstants.LONG_PRESS
        } else {
            duration = buttonPressVibrationMilliseconds.toLong()
            amplitude = buttonPressVibrationAmplitude
            hfc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && keyUp) {
                HapticFeedbackConstants.KEYBOARD_RELEASE
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        }

        // there is `VibrationEffect.DEFAULT_AMPLITUDE` but no default duration;
        // also `VibrationEffect.createOneShot()` only accepts positive duration.
        // so changing amplitude without changing duration makes no sense
        if (duration != 0L) {
            // on Android 13, if system haptic feedback was disabled, `vibrator.vibrate()` won't work
            // but `view.performHapticFeedback()` with `FLAG_IGNORE_GLOBAL_SETTING` still works
            if (hasAmplitudeControl && amplitude != 0) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ve = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(ve)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } else {
            var flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            if (hapticOnKeyPress == InputFeedbackMode.Enabled) {
                // it says "Starting TIRAMISU only privileged apps can ignore user settings for touch feedback"
                // but we still seem to be able to use `FLAG_IGNORE_GLOBAL_SETTING`
                @Suppress("DEPRECATION")
                flags = flags or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            }
            view.performHapticFeedback(hfc, flags)
        }
    }

    enum class SoundEffect {
        Standard, SpaceBar, Delete, Return
    }

    private val appSoundLock = Any()
    private val appSoundIds = IntArray(3)
    private val loadedAppSoundIds = mutableSetOf<Int>()
    private val pendingAppSoundGains = mutableMapOf<Int, Float>()
    private var appSoundPackVersion = -1L

    private fun sampleSoundEffect(effect: SoundEffect): SoundEffect =
        if (effect == SoundEffect.Return) SoundEffect.Delete else effect

    private fun sampleSoundIndex(effect: SoundEffect): Int {
        return when (effect) {
            SoundEffect.Standard -> 0
            SoundEffect.SpaceBar -> 1
            SoundEffect.Delete, SoundEffect.Return -> 2
        }
    }

    private val appSoundPool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .also { pool ->
                pool.setOnLoadCompleteListener { soundPool, sampleId, status ->
                    val gain = synchronized(appSoundLock) {
                        if (status == 0) {
                            loadedAppSoundIds.add(sampleId)
                            pendingAppSoundGains.remove(sampleId)
                        } else {
                            null
                        }
                    }
                    if (gain != null) {
                        soundPool.play(sampleId, gain, gain, 1, 0, 1f)
                    }
                }
            }
    }

    private fun appSoundFile(effect: SoundEffect) = when (effect) {
        SoundEffect.Standard -> UserKeySoundPack.sampleFile(UserKeySoundPack.Sample.Standard)
        SoundEffect.SpaceBar -> UserKeySoundPack.sampleFile(UserKeySoundPack.Sample.Space)
        SoundEffect.Delete, SoundEffect.Return -> UserKeySoundPack.sampleFile(UserKeySoundPack.Sample.Delete)
    }

    private fun builtInDefaultSoundResource(effect: SoundEffect): Int {
        return when (sampleSoundEffect(effect)) {
            SoundEffect.Standard -> R.raw.key_default_standard
            SoundEffect.SpaceBar -> R.raw.key_default_space
            SoundEffect.Delete, SoundEffect.Return -> R.raw.key_default_delete
        }
    }

    private fun refreshAppSoundCacheIfNeeded() {
        val version = UserKeySoundPack.version
        if (appSoundPackVersion == version) return
        appSoundIds.forEach { id ->
            if (id != 0) appSoundPool.unload(id)
        }
        appSoundIds.fill(0)
        loadedAppSoundIds.clear()
        pendingAppSoundGains.clear()
        appSoundPackVersion = version
    }

    private fun appSoundId(effect: SoundEffect): Int {
        refreshAppSoundCacheIfNeeded()
        val sampleEffect = sampleSoundEffect(effect)
        val column = sampleSoundIndex(sampleEffect)
        val current = appSoundIds[column]
        if (current != 0) return current
        val id = if (UserKeySoundPack.usesBuiltInDefaultSounds) {
            appSoundPool.load(appContext, builtInDefaultSoundResource(sampleEffect), 1)
        } else {
            val file = appSoundFile(sampleEffect)
            if (!file.isFile) return 0
            appSoundPool.load(file.absolutePath, 1)
        }
        appSoundIds[column] = id
        return id
    }

    fun preloadAppSoundsIfEnabled() {
        val enabled = when (soundOnKeyPress) {
            InputFeedbackMode.Enabled -> true
            InputFeedbackMode.Disabled -> false
            InputFeedbackMode.FollowingSystem -> systemSoundEffects
        }
        if (!enabled) return
        synchronized(appSoundLock) {
            appSoundId(SoundEffect.Standard)
            appSoundId(SoundEffect.SpaceBar)
            appSoundId(SoundEffect.Delete)
        }
    }

    private fun playAppSoundEffect(effect: SoundEffect, volume: Int) {
        val gain = (volume.coerceIn(0, 100) / 100f).takeIf { it > 0f } ?: 0.5f
        val soundId = synchronized(appSoundLock) {
            val id = appSoundId(effect)
            when {
                id == 0 -> 0
                id in loadedAppSoundIds -> id
                else -> {
                    pendingAppSoundGains[id] = gain
                    0
                }
            }
        }
        if (soundId != 0) {
            appSoundPool.play(soundId, gain, gain, 1, 0, 1f)
        }
    }

    private fun playLoadedAppSoundEffect(effect: SoundEffect, volume: Int) {
        val gain = (volume.coerceIn(0, 100) / 100f).takeIf { it > 0f } ?: 0.5f
        val soundId = synchronized(appSoundLock) {
            if (appSoundPackVersion != UserKeySoundPack.version) return@synchronized 0
            appSoundIds[sampleSoundIndex(sampleSoundEffect(effect))]
                .takeIf(loadedAppSoundIds::contains)
                ?: 0
        }
        if (soundId != 0) {
            appSoundPool.play(soundId, gain, gain, 1, 0, 1f)
        }
    }

    fun soundEffect(effect: SoundEffect) {
        when (soundOnKeyPress) {
            InputFeedbackMode.Enabled -> {
                val volume = soundOnKeyPressVolume.takeIf { it > 0 } ?: 50
                // A key press must never initialize decoders; sounds not ready after the first
                // visible frame are intentionally skipped instead of delaying input.
                playLoadedAppSoundEffect(effect, volume)
                return
            }
            InputFeedbackMode.Disabled -> return
            InputFeedbackMode.FollowingSystem -> if (!systemSoundEffects) return
        }
        playLoadedAppSoundEffect(effect, soundOnKeyPressVolume)
    }

    fun previewSoundEffect(effect: SoundEffect) {
        val volume = soundOnKeyPressVolume.takeIf { it > 0 } ?: 50
        playAppSoundEffect(effect, volume)
    }

}

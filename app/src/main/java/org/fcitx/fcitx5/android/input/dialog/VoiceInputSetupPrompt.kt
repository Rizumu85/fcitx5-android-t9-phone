/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.dialog

import android.app.AlertDialog
import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.utils.AppUtil
import org.fcitx.fcitx5.android.utils.InputMethodUtil
import org.fcitx.fcitx5.android.utils.InputMethodUtil.VoiceInputSetup

/**
 * Explains why voice input is unavailable before leaving the editor. An idle long `0` can trigger
 * voice by accident, so settings open only after the user confirms.
 */
object VoiceInputSetupPrompt {
    fun build(context: Context, setup: VoiceInputSetup): AlertDialog {
        val builder = AlertDialog.Builder(context)
            .setTitle(R.string.voice_input_setup_title)
        return when (setup) {
            is VoiceInputSetup.EnableVoiceInputMethod -> builder
                .setMessage(
                    context.getString(
                        R.string.voice_input_setup_enable_named,
                        setup.labels.joinToString(context.getString(R.string.voice_input_setup_separator))
                    )
                )
                .setPositiveButton(R.string.voice_input_setup_open_settings) { _, _ ->
                    InputMethodUtil.startSettingsActivity(context)
                }
                .setNegativeButton(android.R.string.cancel, null)
            VoiceInputSetup.ChoosePreferredVoiceInput -> builder
                .setMessage(R.string.voice_input_setup_choose_preferred)
                .setPositiveButton(R.string.voice_input_setup_open_settings) { _, _ ->
                    AppUtil.launchMainToInputOptions(context)
                }
                .setNegativeButton(android.R.string.cancel, null)
            VoiceInputSetup.EnableAnyInputMethod -> builder
                .setMessage(R.string.voice_input_setup_enable_any)
                .setPositiveButton(R.string.voice_input_setup_open_settings) { _, _ ->
                    InputMethodUtil.startSettingsActivity(context)
                }
                .setNegativeButton(android.R.string.cancel, null)
            VoiceInputSetup.InstallVoiceInputMethod -> builder
                .setMessage(R.string.voice_input_setup_install)
                .setPositiveButton(android.R.string.ok, null)
        }.create()
    }
}

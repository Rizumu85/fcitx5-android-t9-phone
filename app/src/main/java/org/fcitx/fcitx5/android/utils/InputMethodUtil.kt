/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.input.FcitxInputMethodService

object InputMethodUtil {

    data class VoiceInputTarget(
        val inputMethod: InputMethodInfo,
        val subtype: InputMethodSubtype?
    ) {
        val id: String
            get() = inputMethod.id
    }

    @JvmField
    val serviceName: String = FcitxInputMethodService::class.java.name

    @JvmField
    val componentName: String =
        ComponentName(appContext, FcitxInputMethodService::class.java).flattenToShortString()

    fun isEnabled(): Boolean {
        return appContext.inputMethodManager.enabledInputMethodList.any {
            it.packageName == BuildConfig.APPLICATION_ID && it.serviceName == serviceName
        }
    }

    fun isSelected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appContext.inputMethodManager.currentInputMethodInfo?.let {
                it.packageName == BuildConfig.APPLICATION_ID && it.serviceName == serviceName
            } ?: false
        } else {
            getSecureSettings<String>(Settings.Secure.DEFAULT_INPUT_METHOD) == componentName
        }
    }

    fun startSettingsActivity(context: Context) =
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

    fun showPicker() = appContext.inputMethodManager.showInputMethodPicker()

    private fun enabledExternalInputMethods(): List<InputMethodInfo> =
        appContext.inputMethodManager.enabledInputMethodList.filterNot {
            // Formal and debug builds can be enabled together. The concrete service class keeps
            // either build from ever being offered as its own voice target.
            it.serviceName == serviceName
        }

    private fun InputMethodInfo.voiceIdentity(): String = buildString {
        append(id)
        append(' ')
        append(packageName)
        append(' ')
        append(serviceName)
        append(' ')
        append(runCatching { loadLabel(appContext.packageManager) }.getOrNull())
    }

    fun listVoiceInputMethods(): List<VoiceInputTarget> =
        enabledExternalInputMethods()
            .map { VoiceInputTarget(it, it.firstVoiceSubtype()) }
            .filter { target ->
                target.subtype != null || hasVoiceInputIdentityHint(target.inputMethod.voiceIdentity())
            }
            .sortedBy { it.subtype == null }

    /** Resolve an enabled preferred target before automatic voice-IME discovery. */
    fun findVoiceInputTarget(id: String): VoiceInputTarget? {
        val enabled = enabledExternalInputMethods()
        if (id.isNotEmpty()) {
            enabled.firstOrNull { it.id == id }?.let {
                // An explicit preference is authoritative. This also supports standalone voice
                // IMEs that publish no subtype metadata.
                return VoiceInputTarget(it, it.firstVoiceSubtype())
            }
        }
        return listVoiceInputMethods().firstOrNull()
    }

    fun switchInputMethod(
        service: FcitxInputMethodService,
        id: String,
        subtype: InputMethodSubtype?
    ): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (subtype == null) service.switchInputMethod(id)
            else service.switchInputMethod(id, subtype)
        } else {
            val token = service.window.window?.attributes?.token ?: return false
            @Suppress("DEPRECATION")
            if (subtype == null) {
                appContext.inputMethodManager.setInputMethod(token, id)
            } else {
                appContext.inputMethodManager.setInputMethodAndSubtype(token, id, subtype)
            }
        }
    }.isSuccess

    fun switchToVoiceInput(
        service: FcitxInputMethodService,
        preferredId: String
    ): Boolean {
        val target = findVoiceInputTarget(preferredId) ?: return false
        return switchInputMethod(service, target.id, target.subtype)
    }
}

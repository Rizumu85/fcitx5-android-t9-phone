/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import org.fcitx.fcitx5.android.data.InputFeedbacks

class PhysicalKeySoundAccessibilityService : AccessibilityService() {
    private var observingSystemSoundSetting = false
    private val systemSoundObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            InputFeedbacks.syncSystemPrefs()
            InputFeedbacks.preloadAppSoundsIfEnabled()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val keyOnlyInfo = serviceInfo
        // The XML event type makes the service discoverable across OEM settings apps. Once bound,
        // key filtering needs no accessibility event stream, so disable that IPC path entirely.
        keyOnlyInfo.eventTypes = 0
        keyOnlyInfo.flags =
            keyOnlyInfo.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        setServiceInfo(keyOnlyInfo)
        InputFeedbacks.syncSystemPrefs()
        InputFeedbacks.preloadAppSoundsIfEnabled()
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SOUND_EFFECTS_ENABLED),
            false,
            systemSoundObserver
        )
        observingSystemSoundSetting = true
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        PhysicalKeySoundDispatcher.dispatch(
            PhysicalKeySoundDispatcher.Source.GLOBAL_OBSERVER,
            event
        )
        // This service adds feedback only. Every key retains its original system behavior.
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (observingSystemSoundSetting) {
            contentResolver.unregisterContentObserver(systemSoundObserver)
            observingSystemSoundSetting = false
        }
        super.onDestroy()
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java)
            val expected = ComponentName(context, PhysicalKeySoundAccessibilityService::class.java)
            return manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { service ->
                    val info = service.resolveInfo.serviceInfo
                    ComponentName(info.packageName, info.name) == expected
                }
        }
    }
}

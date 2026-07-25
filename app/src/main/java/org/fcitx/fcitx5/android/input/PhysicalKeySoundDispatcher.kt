/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.view.KeyEvent
import org.fcitx.fcitx5.android.data.InputFeedbacks

object PhysicalKeySoundDispatcher {
    enum class Source {
        INPUT_METHOD,
        GLOBAL_OBSERVER
    }

    private val duplicateFilter = PhysicalKeySoundDuplicateFilter()

    fun dispatch(source: Source, event: KeyEvent) {
        val effect = PhysicalKeySoundPolicy.effect(
            source = source,
            action = event.action,
            repeatCount = event.repeatCount,
            keyCode = event.keyCode
        ) ?: return
        if (!duplicateFilter.accept(event.keyCode, event.deviceId, event.downTime)) return
        InputFeedbacks.soundEffect(effect)
    }
}

internal object PhysicalKeySoundPolicy {
    fun effect(
        source: PhysicalKeySoundDispatcher.Source,
        action: Int,
        repeatCount: Int,
        keyCode: Int
    ): InputFeedbacks.SoundEffect? {
        if (action != KeyEvent.ACTION_DOWN || repeatCount != 0) return null
        // The IME already covers ordinary typing keys. The global observer is deliberately
        // limited to keys Android diverts before IME dispatch, avoiding duplicate volume/dial
        // feedback and keeping the always-on path to one switch and one SoundPool call.
        if (source == PhysicalKeySoundDispatcher.Source.GLOBAL_OBSERVER &&
            !isGloballyObservedKey(keyCode)
        ) {
            return null
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_FORWARD_DEL -> InputFeedbacks.SoundEffect.Delete
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> InputFeedbacks.SoundEffect.Return
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_SELECT -> InputFeedbacks.SoundEffect.SpaceBar
            else -> InputFeedbacks.SoundEffect.Standard
        }
    }

    private fun isGloballyObservedKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_CALL,
        KeyEvent.KEYCODE_ENDCALL -> true
        else -> false
    }
}

internal class PhysicalKeySoundDuplicateFilter {
    private var lastKeyCode = KeyEvent.KEYCODE_UNKNOWN
    private var lastDeviceId = Int.MIN_VALUE
    private var lastDownTime = Long.MIN_VALUE

    @Synchronized
    fun accept(keyCode: Int, deviceId: Int, downTime: Long): Boolean {
        if (keyCode == lastKeyCode && deviceId == lastDeviceId && downTime == lastDownTime) {
            return false
        }
        lastKeyCode = keyCode
        lastDeviceId = deviceId
        lastDownTime = downTime
        return true
    }
}

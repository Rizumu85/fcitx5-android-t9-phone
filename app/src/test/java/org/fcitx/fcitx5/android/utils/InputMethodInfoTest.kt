/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputMethodInfoTest {

    @Test
    fun recognizesStandaloneVoiceImeIdentityWithoutSubtypeMetadata() {
        assertTrue(
            hasVoiceInputIdentityHint(
                "com.google.android.tts.settings.asr.voiceime.VoiceInputMethodService"
            )
        )
        assertTrue(hasVoiceInputIdentityHint("系统语音输入"))
        assertFalse(hasVoiceInputIdentityHint("com.android.inputmethod.latin.LatinIME"))
    }
}

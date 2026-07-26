/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class ChineseT9SchemeActivationSession(
    initialScheme: ChineseT9Scheme = ChineseT9Scheme.PINYIN
) {
    data class Observation(
        val scheme: ChineseT9Scheme,
        val shouldApply: Boolean,
        val forceReset: Boolean
    )

    var activeScheme: ChineseT9Scheme = initialScheme
        private set

    var activeIdentity: String? = null
        private set

    fun observe(identity: String): Observation? {
        val normalizedIdentity = identity.trim()
        val scheme = ChineseT9Scheme.fromRimeIdentityOrNull(normalizedIdentity) ?: return null
        val hadActiveIdentity = activeIdentity != null
        val schemeChanged = activeScheme != scheme

        // One Rime switch can publish the schema id followed by its localized submode name.
        // They are transport aliases for one logical activation and must not restart UI state.
        activeIdentity = normalizedIdentity
        activeScheme = scheme
        return Observation(
            scheme = scheme,
            shouldApply = !hadActiveIdentity || schemeChanged,
            forceReset = hadActiveIdentity && schemeChanged
        )
    }

    fun clearIdentity() {
        activeIdentity = null
    }
}

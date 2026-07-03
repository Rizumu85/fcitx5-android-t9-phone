/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import timber.log.Timber

object T9ResponsivenessTrace {
    const val SlowSectionThresholdNanos = 2_000_000L

    inline fun <T> measure(section: String, block: () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val elapsed = System.nanoTime() - start
            if (elapsed >= SlowSectionThresholdNanos) {
                Timber.d("T9 responsiveness: %s took %.2f ms", section, elapsed / 1_000_000.0)
            }
        }
    }
}

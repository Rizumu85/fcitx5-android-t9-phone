/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlin.math.roundToInt

object T9TextOpticalCenter {
    fun representativeSample(value: CharSequence): String {
        val text = value.toString()
        return when {
            containsCodePoint(text, ::isHanCodePoint) -> HAN_SAMPLE
            containsCodePoint(text, ::isBopomofoCodePoint) -> BOPOMOFO_SAMPLE
            text.any(Char::isLetter) -> LATIN_SAMPLE
            text.isNotBlank() -> String(Character.toChars(text.codePointAt(0)))
            else -> LATIN_SAMPLE
        }
    }

    fun centeredBaselinePx(
        contentCenterPx: Float,
        sampleTopPx: Int,
        sampleBottomPx: Int
    ): Float = contentCenterPx - (sampleTopPx + sampleBottomPx) / 2f

    fun translationPx(
        currentBaselinePx: Float,
        contentCenterPx: Float,
        sampleTopPx: Int,
        sampleBottomPx: Int,
        maxAbsShiftPx: Int
    ): Float {
        val target = centeredBaselinePx(contentCenterPx, sampleTopPx, sampleBottomPx)
        return (target - currentBaselinePx)
            .roundToInt()
            .coerceIn(-maxAbsShiftPx.coerceAtLeast(0), maxAbsShiftPx.coerceAtLeast(0))
            .toFloat()
    }

    private fun containsCodePoint(value: String, predicate: (Int) -> Boolean): Boolean {
        var offset = 0
        while (offset < value.length) {
            val codePoint = value.codePointAt(offset)
            if (predicate(codePoint)) return true
            offset += Character.charCount(codePoint)
        }
        return false
    }

    private fun isHanCodePoint(codePoint: Int): Boolean =
        codePoint == 0x3007 ||
            codePoint in 0x2E80..0x2FD5 ||
            codePoint in 0x3400..0x4DBF ||
            codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2EE5F ||
            codePoint in 0x2F800..0x2FA1F ||
            codePoint in 0x30000..0x323AF

    private fun isBopomofoCodePoint(codePoint: Int): Boolean =
        codePoint in 0x3100..0x312F || codePoint in 0x31A0..0x31BF

    private const val LATIN_SAMPLE = "hg"
    private const val HAN_SAMPLE = "中"
    private const val BOPOMOFO_SAMPLE = "ㄅㄧ"
}

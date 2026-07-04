/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.t9

object T9CandidateBudget {
    private const val MIN = 4
    private const val MAX = 24
    const val ENGLISH_WORD_COST = 2
    const val EMOJI_COST = 1

    fun normalizedBudget(value: Int): Int = value.coerceIn(MIN, MAX)

    fun candidateCost(text: String): Int {
        if (isEnglishWord(text)) return ENGLISH_WORD_COST
        if (text.isEmpty()) return 1

        val codePoints = text.codePoints().toArray()
        var index = 0
        var cost = 0
        while (index < codePoints.size) {
            val codePoint = codePoints[index]
            if (isRegionalIndicator(codePoint) &&
                index + 1 < codePoints.size &&
                isRegionalIndicator(codePoints[index + 1])
            ) {
                cost += EMOJI_COST
                index += 2
            } else if (isKeycapBase(codePoint) && hasKeycapSuffix(codePoints, index + 1)) {
                cost += EMOJI_COST
                index = skipKeycapSuffix(codePoints, index + 1)
            } else if (isEmojiBase(codePoint)) {
                cost += EMOJI_COST
                index = skipEmojiCluster(codePoints, index + 1)
            } else if (isEmojiJoinerOrModifier(codePoint)) {
                index += 1
            } else {
                cost += 1
                index += 1
            }
        }
        return cost.coerceAtLeast(1)
    }

    private fun isEnglishWord(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.all { it in 'A'..'Z' || it in 'a'..'z' || it == '\'' || it == '-' } &&
            trimmed.any { it in 'A'..'Z' || it in 'a'..'z' }
    }

    private fun skipEmojiCluster(codePoints: IntArray, start: Int): Int {
        var index = start
        while (index < codePoints.size) {
            when {
                codePoints[index] == ZERO_WIDTH_JOINER && index + 1 < codePoints.size &&
                    isEmojiBase(codePoints[index + 1]) -> {
                    index += 2
                }
                isEmojiJoinerOrModifier(codePoints[index]) -> index += 1
                else -> return index
            }
        }
        return index
    }

    private fun isEmojiJoinerOrModifier(codePoint: Int): Boolean =
        codePoint == ZERO_WIDTH_JOINER ||
            codePoint == VARIATION_SELECTOR_16 ||
            codePoint in EMOJI_SKIN_TONE_RANGE ||
            codePoint in COMBINING_KEYCAP_RANGE

    private fun isEmojiBase(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x2300..0x23FF ||
            codePoint == 0x00A9 ||
            codePoint == 0x00AE

    private fun isRegionalIndicator(codePoint: Int): Boolean =
        codePoint in REGIONAL_INDICATOR_RANGE

    private fun isKeycapBase(codePoint: Int): Boolean =
        codePoint in '0'.code..'9'.code || codePoint == '#'.code || codePoint == '*'.code

    private fun hasKeycapSuffix(codePoints: IntArray, start: Int): Boolean {
        var index = start
        if (index < codePoints.size && codePoints[index] == VARIATION_SELECTOR_16) {
            index += 1
        }
        return index < codePoints.size && codePoints[index] in COMBINING_KEYCAP_RANGE
    }

    private fun skipKeycapSuffix(codePoints: IntArray, start: Int): Int {
        var index = start
        if (index < codePoints.size && codePoints[index] == VARIATION_SELECTOR_16) {
            index += 1
        }
        if (index < codePoints.size && codePoints[index] in COMBINING_KEYCAP_RANGE) {
            index += 1
        }
        return index
    }

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val VARIATION_SELECTOR_16 = 0xFE0F
    private val REGIONAL_INDICATOR_RANGE = 0x1F1E6..0x1F1FF
    private val EMOJI_SKIN_TONE_RANGE = 0x1F3FB..0x1F3FF
    private val COMBINING_KEYCAP_RANGE = 0x20E3..0x20E3
}

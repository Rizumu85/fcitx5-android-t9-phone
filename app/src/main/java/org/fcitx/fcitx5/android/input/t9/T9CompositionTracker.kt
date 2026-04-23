/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Tracks T9 key sequence (digits 2-9 and apostrophe) for pinyin selection bar.
 */
package org.fcitx.fcitx5.android.input.t9

/**
 * Tracks the current T9 composition so we can show pinyin candidates for the last segment
 * and replace it when user selects a pinyin.
 */
class T9CompositionTracker {

    private val buffer = StringBuilder()

    fun appendDigit(digit: Char) {
        if (digit in '2'..'9') buffer.append(digit)
    }

    fun appendApostrophe() {
        buffer.append('\'')
    }

    fun backspace(): Boolean {
        if (buffer.isEmpty()) return false
        buffer.deleteCharAt(buffer.length - 1)
        return true
    }

    fun clear() {
        buffer.clear()
    }

    fun replace(rawComposition: String) {
        buffer.clear()
        rawComposition.forEach { ch ->
            when {
                ch in '2'..'9' -> buffer.append(ch)
                ch == '\'' -> buffer.append(ch)
            }
        }
    }

    /**
     * Current segment to show pinyin for: from last apostrophe (or start) to end, digits only.
     */
    fun getCurrentSegment(): String {
        val lastApos = buffer.lastIndexOf('\'')
        val segment = if (lastApos < 0) buffer else buffer.substring(lastApos + 1)
        return buildString {
            for (ch in segment) if (ch in '2'..'9') append(ch)
        }
    }

    /**
     * Length of the current segment in keys (for backspace count when replacing with pinyin).
     */
    fun getCurrentSegmentKeyLength(): Int = getCurrentSegment().length

    /**
     * After user selects a pinyin: remove the current segment from tracker (engine state
     * will be updated by sending backspace + pinyin letters from the service).
     */
    fun removeCurrentSegment() {
        val seg = getCurrentSegment()
        if (seg.isEmpty()) return
        val lastApos = buffer.lastIndexOf('\'')
        val start = if (lastApos < 0) 0 else lastApos + 1
        val end = start + seg.length
        buffer.delete(start, end)
    }

    fun isEmpty(): Boolean = buffer.isEmpty()

    /** Full composition (digits and apostrophes) for building pinyin preedit display. */
    fun getFullComposition(): String = buffer.toString()
}

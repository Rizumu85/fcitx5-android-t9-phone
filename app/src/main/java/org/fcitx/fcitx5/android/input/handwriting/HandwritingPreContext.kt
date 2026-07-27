/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

internal class HandwritingPreContext {
    private var text = ""

    fun begin(editorPreContext: String) {
        text = bound(editorPreContext)
    }

    fun append(committedText: String) {
        text = bound(text + committedText)
    }

    fun clear() {
        text = ""
    }

    fun snapshot(): String = text

    companion object {
        // ML Kit recommends the longest useful editor suffix up to roughly 20 characters.
        private const val MaximumCodePoints = 20

        fun bound(editorPreContext: String): String {
            val count = editorPreContext.codePointCount(0, editorPreContext.length)
            if (count <= MaximumCodePoints) return editorPreContext
            return editorPreContext.substring(
                editorPreContext.offsetByCodePoints(0, count - MaximumCodePoints)
            )
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.rendering.android.view.ViewStrokeRenderer
import androidx.ink.strokes.Stroke

/** Stable layer for completed strokes; the front buffer remains dedicated to the active stroke. */
internal class FinishedHandwritingStrokesView(context: Context) : View(context) {
    private val renderer = ViewStrokeRenderer(CanvasStrokeRenderer.create(), this)
    private var strokes: List<Stroke> = emptyList()

    fun setStrokes(value: List<Stroke>) {
        if (strokes == value) return
        strokes = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        renderer.drawWithStrokes(canvas) { scope ->
            strokes.forEach(scope::drawStroke)
        }
    }
}

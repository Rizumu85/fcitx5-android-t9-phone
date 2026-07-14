/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import kotlin.math.ceil
import kotlin.math.max

object T9SemanticTextStyler {
    fun decorate(text: CharSequence): CharSequence {
        if (!text.contains(T9PunctuationSession.NewlineSymbol)) return text
        return SpannableString(text).apply {
            var start = indexOf(T9PunctuationSession.NewlineSymbol)
            while (start >= 0) {
                setSpan(
                    ReturnSymbolSpan(),
                    start,
                    start + T9PunctuationSession.NewlineSymbol.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                start = indexOf(T9PunctuationSession.NewlineSymbol, start + 1)
            }
        }
    }

    private class ReturnSymbolSpan : ReplacementSpan() {
        private val path = Path()
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int = ceil(paint.textSize * ADVANCE_EM).toInt()

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val advance = paint.textSize * ADVANCE_EM
            val width = paint.textSize * GLYPH_WIDTH_EM
            val height = paint.textSize * GLYPH_HEIGHT_EM
            val centerX = x + advance / 2f
            val centerY = (top + bottom) / 2f
            val left = centerX - width / 2f
            val right = centerX + width / 2f
            val glyphTop = centerY - height / 2f
            val shaftY = centerY + height * SHAFT_Y_RATIO
            val arrowWingX = left + width * ARROW_WING_X_RATIO
            val arrowWingY = height * ARROW_WING_Y_RATIO
            val turnRadius = width * TURN_RADIUS_RATIO

            stroke.set(paint)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = max(MIN_STROKE_PX, paint.textSize * STROKE_EM)
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.strokeJoin = Paint.Join.ROUND
            stroke.isAntiAlias = true
            path.reset()
            path.moveTo(right, glyphTop)
            path.lineTo(right, shaftY - turnRadius)
            path.quadTo(right, shaftY, right - turnRadius, shaftY)
            path.lineTo(left, shaftY)
            path.moveTo(arrowWingX, shaftY - arrowWingY)
            path.lineTo(left, shaftY)
            path.lineTo(arrowWingX, shaftY + arrowWingY)
            canvas.drawPath(path, stroke)
        }
    }

    private const val ADVANCE_EM = 1.18f
    private const val GLYPH_WIDTH_EM = 0.88f
    private const val GLYPH_HEIGHT_EM = 0.82f
    private const val STROKE_EM = 0.085f
    private const val MIN_STROKE_PX = 1.5f
    private const val SHAFT_Y_RATIO = 0.14f
    private const val ARROW_WING_X_RATIO = 0.24f
    private const val ARROW_WING_Y_RATIO = 0.22f
    private const val TURN_RADIUS_RATIO = 0.22f
}

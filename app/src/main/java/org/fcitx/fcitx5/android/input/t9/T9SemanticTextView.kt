/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.widget.TextView
import org.fcitx.fcitx5.android.input.InputUiFont
import kotlin.math.ceil
import kotlin.math.max

class T9SemanticTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {
    private val symbolPath = Path()
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val visibleClip = Rect()
    var semanticSymbolScale: Float = 1f
        set(value) {
            field = value.coerceAtLeast(0.1f)
            requestLayout()
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!handles(text)) return
        val desiredWidth = ceil(measureWidth(paint, text.toString(), semanticSymbolScale)).toInt() +
            compoundPaddingLeft + compoundPaddingRight
        setMeasuredDimension(
            resolveSizeAndState(
                max(suggestedMinimumWidth, desiredWidth),
                widthMeasureSpec,
                measuredState
            ),
            measuredHeight
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (!handles(text)) {
            super.onDraw(canvas)
            return
        }
        // Product decision: the return action is semantic artwork rather than a font glyph.
        // Centering it in the actual content box keeps custom fonts and compact preview rows from
        // changing its apparent size or baseline.
        // Floating candidate windows can preserve a translated hardware-canvas clip. Deriving the
        // local drawing origin from that clip matches TextView's own renderer instead of assuming
        // the visible viewport starts at canvas coordinate zero.
        val hasClip = canvas.getClipBounds(visibleClip)
        val originX = if (hasClip) visibleClip.left.toFloat() else 0f
        val originY = if (hasClip) visibleClip.top.toFloat() else 0f
        val contentLeft = originX + compoundPaddingLeft
        val contentRight = originX + width - compoundPaddingRight
        val contentTop = originY + extendedPaddingTop
        val contentBottom = originY + height - extendedPaddingBottom
        val em = paint.textSize * semanticSymbolScale
        val glyphWidth = em * GLYPH_WIDTH_EM
        val glyphHeight = em * GLYPH_HEIGHT_EM
        val centerX = (contentLeft + contentRight) / 2f
        val centerY = (contentTop + contentBottom) / 2f
        val left = centerX - glyphWidth / 2f
        val right = centerX + glyphWidth / 2f
        val glyphTop = centerY - glyphHeight / 2f
        val shaftY = centerY + glyphHeight * SHAFT_Y_RATIO
        val arrowWingX = left + glyphWidth * ARROW_WING_X_RATIO
        val arrowWingY = glyphHeight * ARROW_WING_Y_RATIO
        val turnRadius = glyphWidth * TURN_RADIUS_RATIO

        symbolPaint.reset()
        symbolPaint.isAntiAlias = true
        symbolPaint.color = currentTextColor
        symbolPaint.style = Paint.Style.STROKE
        symbolPaint.strokeWidth = max(
            MIN_STROKE_PX,
            em * T9SemanticSymbolStyle.strokeEmForWeight(resolvedFontWeight())
        )
        symbolPaint.strokeCap = Paint.Cap.ROUND
        symbolPaint.strokeJoin = Paint.Join.MITER
        symbolPath.reset()
        symbolPath.moveTo(right, glyphTop)
        symbolPath.lineTo(right, shaftY - turnRadius)
        symbolPath.quadTo(right, shaftY, right - turnRadius, shaftY)
        symbolPath.lineTo(left, shaftY)
        symbolPath.moveTo(arrowWingX, shaftY - arrowWingY)
        symbolPath.lineTo(left, shaftY)
        symbolPath.lineTo(arrowWingX, shaftY + arrowWingY)
        canvas.drawPath(symbolPath, symbolPaint)
    }

    private fun resolvedFontWeight(): Int {
        // Typeface.create(base, NORMAL) can report 400 even when a static custom font contains
        // semibold outlines. Prefer the source font's parsed OpenType weight when available.
        val declaredWeight = InputUiFont.selectedFontWeight()
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) paint.typeface?.weight else null
        val baseWeight = declaredWeight ?: if (paint.typeface?.isBold == true) {
            T9SemanticSymbolStyle.BoldFontWeight
        } else {
            T9SemanticSymbolStyle.NormalFontWeight
        }
        return if (paint.isFakeBoldText) {
            max(baseWeight, T9SemanticSymbolStyle.BoldFontWeight)
        } else {
            baseWeight
        }
    }

    companion object {
        fun handles(text: CharSequence): Boolean =
            text.toString() == T9PunctuationSession.NewlineSymbol

        fun measureWidth(paint: Paint, text: String, symbolScale: Float = 1f): Float =
            if (handles(text)) paint.textSize * symbolScale * ADVANCE_EM else paint.measureText(text)

        private const val ADVANCE_EM = 1.18f
        private const val GLYPH_WIDTH_EM = 0.88f
        private const val GLYPH_HEIGHT_EM = 0.82f
        private const val MIN_STROKE_PX = 1.5f
        private const val SHAFT_Y_RATIO = 0.14f
        private const val ARROW_WING_X_RATIO = 0.24f
        private const val ARROW_WING_Y_RATIO = 0.22f
        // Font files expose weight but not a reliable sharp-versus-rounded personality. A fixed,
        // moderate bend remains legible beside both geometric and soft custom typefaces.
        private const val TURN_RADIUS_RATIO = 0.10f
    }
}

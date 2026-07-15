/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.editing

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min

/** A single cross-shaped surface keeps cursor movement feeling like one physical control. */
class TextEditingDpadDrawable(
    color: Int,
    private val radius: Float,
    shadowColor: Int,
    private val shadowOffset: Int
) : Drawable() {

    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = shadowColor }
    private val horizontal = RectF()
    private val vertical = RectF()

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        if (shadowOffset > 0) drawCross(canvas, shadowPaint, shadowOffset.toFloat())
        drawCross(canvas, surfacePaint, 0f)
    }

    private fun drawCross(canvas: Canvas, paint: Paint, yOffset: Float) {
        val cellWidth = bounds.width() / 3f
        val cellHeight = bounds.height() / 3f
        val bottomInset = if (paint === surfacePaint) shadowOffset.toFloat() else 0f
        val effectiveRadius = min(radius, min(cellWidth, cellHeight) / 2f)

        horizontal.set(
            bounds.left.toFloat(),
            bounds.top + cellHeight + yOffset,
            bounds.right.toFloat(),
            bounds.top + cellHeight * 2f + yOffset - bottomInset
        )
        vertical.set(
            bounds.left + cellWidth,
            bounds.top.toFloat() + yOffset,
            bounds.left + cellWidth * 2f,
            bounds.bottom.toFloat() + yOffset - bottomInset
        )
        canvas.drawRoundRect(horizontal, effectiveRadius, effectiveRadius, paint)
        canvas.drawRoundRect(vertical, effectiveRadius, effectiveRadius, paint)
    }

    override fun setAlpha(alpha: Int) {
        surfacePaint.alpha = alpha
        shadowPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        surfacePaint.colorFilter = colorFilter
        shadowPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.view.View
import androidx.annotation.Size
import kotlin.math.roundToInt

class FloatingCandidateWindowController(
    private val host: Host
) {
    interface Host {
        val visibility: Int
        val width: Int
        val height: Int
        val minWidth: Int
        var maxWidth: Int

        fun requestLayout()
        fun invalidate()
        fun setTranslation(x: Float, y: Float)
        fun setVisibilityImmediately(visibility: Int)
        fun showTouchReceiverAt(x: Int, y: Int, width: Int, height: Int)
        fun dismissTouchReceiver()
    }

    data class PositionConfig(
        val horizontalMarginPx: Int,
        val shadowOutsetPx: Int
    )

    private val anchorPosition = floatArrayOf(0f, 0f, 0f)
    private val parentSize = floatArrayOf(0f, 0f)

    private var shouldUpdatePosition = false
    private var showAfterPositioned = false
    private var showAfterPositionedContentReady = true
    private var preferAboveCursorAnchor = false
    private var bottomInsets = 0

    val isWaitingForPosition: Boolean
        get() = showAfterPositioned

    val parentWidthPx: Int
        get() = parentSize[0].roundToInt()

    fun setPreferAboveCursorAnchor(preferAboveCursorAnchor: Boolean) {
        this.preferAboveCursorAnchor = preferAboveCursorAnchor
    }

    fun setBottomInsets(bottomInsets: Int) {
        this.bottomInsets = bottomInsets
    }

    fun requestPositionUpdate() {
        shouldUpdatePosition = true
    }

    fun onPreDraw(config: PositionConfig) {
        if (shouldUpdatePosition || showAfterPositioned) {
            updatePosition(config)
        }
    }

    fun showWhenPositioned(contentReady: Boolean) {
        showAfterPositionedContentReady = contentReady
        if (!contentReady && host.visibility != View.VISIBLE) {
            showAfterPositioned = true
            shouldUpdatePosition = true
            host.setVisibilityImmediately(View.INVISIBLE)
            host.requestLayout()
            host.invalidate()
            return
        }
        if (host.visibility == View.VISIBLE) {
            shouldUpdatePosition = true
            host.requestLayout()
            host.invalidate()
            return
        }
        showAfterPositioned = true
        shouldUpdatePosition = true
        host.setVisibilityImmediately(View.INVISIBLE)
        host.requestLayout()
        host.invalidate()
    }

    fun onSurfaceHidden() {
        showAfterPositioned = false
        showAfterPositionedContentReady = true
        host.dismissTouchReceiver()
    }

    fun onDetached() {
        host.dismissTouchReceiver()
    }

    fun updateCursorAnchor(
        @Size(4) anchor: FloatArray,
        @Size(2) parent: FloatArray,
        config: PositionConfig
    ) {
        val (horizontal, bottom, _, top) = anchor
        val (parentWidth, parentHeight) = parent
        anchorPosition[0] = horizontal
        anchorPosition[1] = bottom
        anchorPosition[2] = top
        parentSize[0] = parentWidth
        parentSize[1] = parentHeight
        updatePosition(config)
    }

    fun updatePosition(config: PositionConfig) {
        val (parentWidth, parentHeight) = parentSize
        if (parentWidth > 0) {
            val windowMargin = (config.horizontalMarginPx - config.shadowOutsetPx).coerceAtLeast(0)
            val maxW = (parentWidth - 2 * windowMargin).toInt().coerceAtLeast(host.minWidth)
            if (host.maxWidth != maxW) {
                host.maxWidth = maxW
                host.requestLayout()
            }
        }
        if (host.visibility != View.VISIBLE && !showAfterPositioned) {
            return
        }
        if (showAfterPositioned && (host.width <= 0 || host.height <= 0)) {
            shouldUpdatePosition = true
            host.requestLayout()
            return
        }
        if (parentWidth <= 0 || parentHeight <= 0) {
            host.setTranslation(0f, 0f)
            return
        }
        val (_, bottom, top) = anchorPosition
        val width = host.width
        val height = host.height
        val selfHeight = height.toFloat()
        val translationX = (config.horizontalMarginPx - config.shadowOutsetPx)
            .coerceAtLeast(0)
            .toFloat()
        val bottomLimit = parentHeight - bottomInsets
        val bottomSpace = bottomLimit - bottom
        val translationY = if (preferAboveCursorAnchor) {
            val maxY = (bottomLimit - selfHeight).coerceAtLeast(0f)
            (top - selfHeight).coerceIn(0f, maxY)
        } else if (
            bottom + selfHeight > bottomLimit &&
            top > bottomSpace
        ) {
            top - selfHeight
        } else {
            bottom
        }
        host.setTranslation(translationX, translationY)
        if (showAfterPositioned) {
            if (!showAfterPositionedContentReady) {
                // Product decision: the first Chinese T9 pinyin-filter frame should appear as one
                // complete bubble. Positioning may finish before the pinyin row has a trustworthy
                // width, so keep the whole candidate surface invisible until that row is ready.
                shouldUpdatePosition = true
                return
            }
            showAfterPositioned = false
            host.setVisibilityImmediately(View.VISIBLE)
        }
        host.showTouchReceiverAt(
            translationX.roundToInt(),
            translationY.roundToInt(),
            width,
            height
        )
        shouldUpdatePosition = false
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingCandidateWindowControllerTest {
    @Test
    fun waitsForMeasuredSizeBeforeShowingPendingSurface() {
        val host = FakeHost(
            visibility = View.INVISIBLE,
            width = 0,
            height = 0
        )
        val controller = FloatingCandidateWindowController(host)

        controller.updateCursorAnchor(
            anchor = floatArrayOf(10f, 120f, 0f, 80f),
            parent = floatArrayOf(300f, 500f),
            config = config()
        )
        controller.showWhenPositioned(contentReady = true)
        controller.onPreDraw(config())

        assertEquals(View.INVISIBLE, host.visibility)
        assertEquals(0, host.showTouchCalls)
        assertTrue(host.requestLayoutCalls > 0)
    }

    @Test
    fun showsCompleteSurfaceAfterPositionAndContentAreReady() {
        val host = FakeHost(
            visibility = View.INVISIBLE,
            width = 120,
            height = 40
        )
        val controller = FloatingCandidateWindowController(host)

        controller.updateCursorAnchor(
            anchor = floatArrayOf(10f, 120f, 0f, 80f),
            parent = floatArrayOf(300f, 500f),
            config = config(horizontalMarginPx = 16, shadowOutsetPx = 4)
        )
        controller.showWhenPositioned(contentReady = true)
        controller.onPreDraw(config(horizontalMarginPx = 16, shadowOutsetPx = 4))

        assertEquals(View.VISIBLE, host.visibility)
        assertEquals(12f, host.translationX)
        assertEquals(120f, host.translationY)
        assertEquals(1, host.showTouchCalls)
        assertEquals(12, host.touchX)
        assertEquals(120, host.touchY)
        assertEquals(276, host.maxWidth)
    }

    @Test
    fun keepsSurfaceInvisibleUntilContentReady() {
        val host = FakeHost(
            visibility = View.INVISIBLE,
            width = 120,
            height = 40
        )
        val controller = FloatingCandidateWindowController(host)

        controller.updateCursorAnchor(
            anchor = floatArrayOf(10f, 120f, 0f, 80f),
            parent = floatArrayOf(300f, 500f),
            config = config()
        )
        controller.showWhenPositioned(contentReady = false)
        controller.onPreDraw(config())

        assertEquals(View.INVISIBLE, host.visibility)
        assertEquals(0, host.showTouchCalls)

        controller.showWhenPositioned(contentReady = true)
        controller.onPreDraw(config())

        assertEquals(View.VISIBLE, host.visibility)
        assertEquals(1, host.showTouchCalls)
    }

    @Test
    fun placesSurfaceAboveCursorWhenBottomSpaceIsSmaller() {
        val host = FakeHost(
            visibility = View.VISIBLE,
            width = 120,
            height = 80
        )
        val controller = FloatingCandidateWindowController(host)

        controller.updateCursorAnchor(
            anchor = floatArrayOf(10f, 180f, 0f, 100f),
            parent = floatArrayOf(300f, 220f),
            config = config()
        )

        assertEquals(20f, host.translationY)
    }

    @Test
    fun hidesPendingSurfaceAndDismissesTouchReceiver() {
        val host = FakeHost(
            visibility = View.VISIBLE,
            width = 120,
            height = 40
        )
        val controller = FloatingCandidateWindowController(host)

        controller.showWhenPositioned(contentReady = true)
        controller.onSurfaceHidden()

        assertEquals(1, host.dismissCalls)
        assertEquals(false, controller.isWaitingForPosition)
    }

    private fun config(
        horizontalMarginPx: Int = 12,
        shadowOutsetPx: Int = 4
    ): FloatingCandidateWindowController.PositionConfig =
        FloatingCandidateWindowController.PositionConfig(
            horizontalMarginPx = horizontalMarginPx,
            shadowOutsetPx = shadowOutsetPx
        )

    private class FakeHost(
        override var visibility: Int,
        override var width: Int,
        override var height: Int
    ) : FloatingCandidateWindowController.Host {
        override var minWidth: Int = 40
        override var maxWidth: Int = 0
        var requestLayoutCalls: Int = 0
        var invalidateCalls: Int = 0
        var showTouchCalls: Int = 0
        var dismissCalls: Int = 0
        var translationX: Float = 0f
        var translationY: Float = 0f
        var touchX: Int = 0
        var touchY: Int = 0

        override fun requestLayout() {
            requestLayoutCalls += 1
        }

        override fun invalidate() {
            invalidateCalls += 1
        }

        override fun setTranslation(x: Float, y: Float) {
            translationX = x
            translationY = y
        }

        override fun setVisibilityImmediately(visibility: Int) {
            this.visibility = visibility
        }

        override fun showTouchReceiverAt(x: Int, y: Int, width: Int, height: Int) {
            showTouchCalls += 1
            touchX = x
            touchY = y
            this.width = width
            this.height = height
        }

        override fun dismissTouchReceiver() {
            dismissCalls += 1
        }
    }
}

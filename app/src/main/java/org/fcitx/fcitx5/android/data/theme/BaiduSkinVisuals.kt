/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.util.LruCache
import java.io.File

object BaiduSkinKey {
    const val Generic = "generic"
    const val GenericFunction = "function"
    const val Symbol = "symbol"
    const val Language = "language"
    const val Space = "space"
    const val Backspace = "backspace"
    const val Return = "return"
    const val Caps = "caps"

    private val functionKeys = setOf(
        GenericFunction, Symbol, Language, Space, Backspace, Return, Caps
    )

    fun letter(character: String): String = "letter_${character.lowercase()}"
    fun number(character: String): String = "number_$character"
    fun isFunction(key: String?): Boolean = key in functionKeys
}

internal data class BaiduKeyDrawables(
    val background: Drawable,
    val foreground: Drawable?
)

internal object BaiduSkinVisuals {
    private val bitmapCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val aspectRatioCache = LruCache<String, Float>(128)

    fun resolve(theme: Theme, requestedKey: String?): BaiduKeyDrawables? {
        val (skin, visual) = findVisual(theme, requestedKey) ?: return null
        val normalBackground = drawable(skin.resolve(visual.normalBackground)) ?: return null
        val background = stateDrawable(
            normalBackground,
            visual.pressedBackground?.let(skin::resolve)?.let(::drawable)
        )
        val foreground = visual.normalForeground?.let(skin::resolve)?.let(::drawable)?.let { normal ->
            stateDrawable(
                normal,
                visual.pressedForeground?.let(skin::resolve)?.let(::drawable)
            )
        }
        return BaiduKeyDrawables(background, foreground)
    }

    fun aspectRatio(theme: Theme, requestedKey: String?): Float? {
        val (skin, visual) = findVisual(theme, requestedKey) ?: return null
        val file = skin.resolve(visual.normalBackground)
        aspectRatioCache[file.absolutePath]?.let { return it }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return (options.outWidth.toFloat() / options.outHeight).also {
            aspectRatioCache.put(file.absolutePath, it)
        }
    }

    private fun findVisual(
        theme: Theme,
        requestedKey: String?
    ): Pair<Theme.Custom.BaiduSkin, Theme.Custom.BaiduSkin.KeyVisual>? {
        val skin = (theme as? Theme.Custom)?.baiduSkin ?: return null
        val visual = requestedKey?.let(skin.keys::get)
            ?: skin.keys[BaiduSkinKey.GenericFunction].takeIf {
                BaiduSkinKey.isFunction(requestedKey)
            }
            ?: skin.keys[BaiduSkinKey.Generic]
            ?: return null
        return skin to visual
    }

    private fun stateDrawable(normal: Drawable, pressed: Drawable?): Drawable =
        if (pressed == null) normal else StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }

    private fun drawable(file: File): Drawable? {
        if (!file.isFile) return null
        val bitmap = bitmapCache[file.absolutePath] ?: BitmapFactory.decodeFile(file.absolutePath)
            ?.also { bitmapCache.put(file.absolutePath, it) }
            ?: return null
        return FitBitmapDrawable(bitmap)
    }

    // BDS tiles already contain optical padding and shadows. Center-fitting the complete tile
    // avoids deforming wide keys such as Space when our touch layout has a different aspect ratio.
    private class FitBitmapDrawable(
        private val bitmap: Bitmap
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val destination = RectF()

        override fun draw(canvas: Canvas) {
            val scale = minOf(
                bounds.width().toFloat() / bitmap.width,
                bounds.height().toFloat() / bitmap.height
            )
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val left = bounds.left + (bounds.width() - width) / 2f
            val top = bounds.top + (bounds.height() - height) / 2f
            destination.set(left, top, left + width, top + height)
            canvas.drawBitmap(bitmap, null, destination, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = bitmap.width
        override fun getIntrinsicHeight(): Int = bitmap.height
    }
}

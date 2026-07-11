/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme.bds

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.max

internal object BdsSkinConverter {
    data class Palette(
        val background: Int,
        val bar: Int,
        val key: Int,
        val text: Int,
        val accent: Int
    )

    data class Variant(
        val sourceName: String,
        val isDark: Boolean,
        val panelPng: ByteArray,
        val palette: Palette
    )

    fun convert(archive: BdsArchive, fallbackName: String): List<Variant> {
        val roots = buildList {
            if (archive.paths.any { it.startsWith("light/") }) add("light")
            if (archive.paths.any { it.startsWith("dark/") }) add("dark")
            if (isEmpty()) add("")
        }
        val baseName = readSkinName(archive, fallbackName)
        return roots.mapNotNull { root -> convertVariant(archive, root, baseName) }
            .also { require(it.isNotEmpty()) { "No compatible portrait BDS skin was found" } }
    }

    private fun convertVariant(archive: BdsArchive, root: String, name: String): Variant? {
        val prefix = root.takeIf { it.isNotEmpty() }?.plus('/') ?: ""
        val layoutRoot = when {
            archive.bytes("${prefix}port/gen.ini") != null -> "${prefix}port"
            archive.bytes("${prefix}land/gen.ini") != null -> "${prefix}land"
            else -> return null
        }
        val resourceRoot = "${prefix}res"
        val gen = archive.document("$layoutRoot/gen.ini") ?: return null
        val css = archive.document("$resourceRoot/default.css") ?: return null
        val panelStyle = gen["PANEL", "BACK_STYLE"] ?: return null
        val panel = resolveStyleImage(archive, css, resourceRoot, panelStyle) ?: return null

        val candidateLayout = gen["CAND", "LAYOUT_NAME"]
        val candidateDocument = candidateLayout?.let {
            archive.document("$layoutRoot/${it.substringBeforeLast('.', it)}.cnd")
        }
        val candidateImage = candidateDocument?.get("CAND", "BACK_STYLE")?.let {
            resolveStyleImage(archive, css, resourceRoot, it)
        }
        val candidateText = candidateDocument?.get("CAND", "FORE_STYLE")?.let {
            parseColor(css["STYLE$it", "NM_COLOR"])
        }

        val nineKey = archive.document("$layoutRoot/py_9.ini")
            ?: archive.document("$layoutRoot/def_9.ini")
        val regularKeyImage = nineKey?.get("KEY2", "BACK_STYLE")?.let {
            resolveStyleImage(archive, css, resourceRoot, it)
        }
        val accentKeyImage = nineKey?.get("KEY12", "BACK_STYLE")?.let {
            resolveStyleImage(archive, css, resourceRoot, it)
        }

        val background = panel.averageColor()
        val key = regularKeyImage.averageColorAndRecycle() ?: background
        val text = candidateText ?: contrastingText(key)
        val accentFromKey = accentKeyImage.averageColorAndRecycle()
        val accent = stylesheetAccent(css) ?: accentFromKey ?: text
        val bar = candidateImage.averageColorAndRecycle() ?: background
        return Variant(
            sourceName = name,
            isDark = root == "dark" || isDark(background),
            panelPng = panel.toPng(),
            palette = Palette(background, bar, key, text, accent)
        )
    }

    private fun readSkinName(archive: BdsArchive, fallback: String): String {
        val infoPath = listOf("info.txt", "light/info.txt", "dark/info.txt")
            .firstOrNull { archive.bytes(it) != null }
        val name = infoPath?.let { archive.document(it)?.get("", "NAME") }
        return name?.replace('_', ' ')?.trim()?.ifEmpty { null } ?: fallback
    }

    private fun BdsArchive.document(path: String): BdsDocument? =
        text(path)?.let(BdsDocument::parse)

    private fun resolveStyleImage(
        archive: BdsArchive,
        css: BdsDocument,
        resourceRoot: String,
        styleId: String
    ): Bitmap? {
        val imageRef = css["STYLE${styleId.trim()}", "NM_IMG"] ?: return null
        val parts = imageRef.split(',').map(String::trim)
        val atlasName = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return null
        val tileIndex = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val atlasBytes = archive.firstBytes(
            "$resourceRoot/$atlasName.png",
            "$resourceRoot/$atlasName.webp"
        ) ?: return null
        val atlas = BitmapFactory.decodeByteArray(atlasBytes, 0, atlasBytes.size) ?: return null
        val tile = archive.document("$resourceRoot/$atlasName.til")
            ?.get("IMG$tileIndex", "SOURCE_RECT")
            ?.let(::parseRect)
        if (tile == null) return atlas
        val bounded = Rect(
            tile.left.coerceIn(0, atlas.width),
            tile.top.coerceIn(0, atlas.height),
            tile.right.coerceIn(0, atlas.width),
            tile.bottom.coerceIn(0, atlas.height)
        )
        if (bounded.width() <= 0 || bounded.height() <= 0) return atlas
        return Bitmap.createBitmap(atlas, bounded.left, bounded.top, bounded.width(), bounded.height())
            .also { if (it !== atlas) atlas.recycle() }
    }

    private fun parseRect(value: String): Rect? {
        val values = value.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (values.size != 4) return null
        return Rect(values[0], values[1], values[0] + values[2], values[1] + values[3])
    }

    private fun parseColor(value: String?): Int? {
        val raw = value?.trim()?.removePrefix("#") ?: return null
        val normalized = when {
            raw.length >= 8 -> raw.take(8)
            raw.length == 6 -> "ff$raw"
            else -> return null
        }
        return normalized.toLongOrNull(16)?.toInt()
    }

    private fun stylesheetAccent(css: BdsDocument): Int? = css.values("NM_COLOR")
        .mapNotNull(::parseColor)
        .filter { Color.alpha(it) >= 128 }
        .map { color ->
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            color to (hsv[1] * max(0.2f, hsv[2]))
        }
        .filter { (_, score) -> score >= 0.25f }
        .maxByOrNull { it.second }
        ?.first

    private fun Bitmap.averageColor(): Int {
        val step = max(1, max(width, height) / 96)
        var red = 0L; var green = 0L; var blue = 0L; var count = 0L
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = getPixel(x, y)
                if (Color.alpha(pixel) >= 32) {
                    red += Color.red(pixel); green += Color.green(pixel); blue += Color.blue(pixel)
                    count++
                }
                x += step
            }
            y += step
        }
        if (count == 0L) return Color.TRANSPARENT
        return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private fun Bitmap?.averageColorAndRecycle(): Int? = this?.let { bitmap ->
        bitmap.averageColor().also { bitmap.recycle() }
    }

    private fun Bitmap.toPng(): ByteArray = ByteArrayOutputStream().use { output ->
        check(compress(Bitmap.CompressFormat.PNG, 100, output))
        recycle()
        output.toByteArray()
    }

    private fun contrastingText(background: Int): Int =
        if (isDark(background)) Color.WHITE else Color.BLACK

    private fun isDark(color: Int): Boolean =
        Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114 < 128000
}

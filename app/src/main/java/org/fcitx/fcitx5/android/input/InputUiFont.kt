/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import android.os.Environment
import android.text.TextPaint
import android.widget.TextView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import java.io.File

object InputUiFont {
    const val SystemDefaultValue = ""

    private const val FontFilePrefix = "font-file:"
    private const val CustomFontsDirName = "fonts"
    private const val PublicFontsDirName = "Fonts"

    private val fontExtensions = setOf("ttf", "otf", "ttc")

    private data class FontFileEntry(val file: File)

    @Volatile
    private var cachedTypefaceValue: String? = null

    @Volatile
    private var cachedTypeface: Typeface? = null

    fun customFontPreferenceEntries(context: Context): List<Pair<String, CharSequence>> {
        val entries = mutableListOf(SystemDefaultValue to context.getString(R.string.system_default))
        entries += customFontFiles(context).map { fontFileValue(it.file) to fontLabel(it) }
        return entries.distinctBy { it.first }.sortedWith(
            compareBy<Pair<String, CharSequence>> { it.first.isNotEmpty() }
                .thenBy { it.second.toString().lowercase() }
        )
    }

    private fun selectedTypeface(): Typeface? {
        val value = AppPrefs.getInstance().keyboard.inputUiFont.getValue()
        if (value == SystemDefaultValue || !value.startsWith(FontFilePrefix)) return null
        // Decision: candidate rendering can measure text dozens of times per key press, so a
        // custom font path must not hit Typeface.createFromFile on the input hot path.
        cachedTypefaceValue.takeIf { it == value }?.let { return cachedTypeface }
        return synchronized(this) {
            cachedTypefaceValue.takeIf { it == value }?.let { return@synchronized cachedTypeface }
            typefaceFromFontFileValue(value).also {
                cachedTypefaceValue = value
                cachedTypeface = it
            }
        }
    }

    fun applyTo(view: TextView, style: Int = Typeface.NORMAL) {
        view.setTypeface(selectedTypeface(), style)
    }

    fun applyTo(paint: TextPaint, style: Int = Typeface.NORMAL) {
        paint.typeface = Typeface.create(selectedTypeface(), style)
    }

    fun applyWeightTo(
        view: TextView,
        weight: Int,
        italic: Boolean = false,
        fallbackStyle: Int = Typeface.BOLD
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.typeface = Typeface.create(selectedTypeface() ?: Typeface.DEFAULT, weight, italic)
        } else {
            applyTo(view, fallbackStyle)
        }
    }

    private fun customFontFiles(context: Context): List<FontFileEntry> {
        val appFontsDir = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve(CustomFontsDirName)
            .also { it.mkdirs() }
        val publicFontsDir = Environment.getExternalStorageDirectory().resolve(PublicFontsDirName)
        return listOf(appFontsDir, publicFontsDir)
            .flatMap { dir -> fontFilesIn(dir).map(::FontFileEntry) }
            .sortedBy { it.file.name.lowercase() }
    }

    private fun fontFilesIn(dir: File): List<File> {
        return runCatching {
            dir.listFiles { file ->
                file.isFile && file.extension.lowercase() in fontExtensions
            }?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun fontLabel(entry: FontFileEntry): String {
        val name = entry.file.nameWithoutExtension
        return if (name.length <= 32) name else "${name.take(31)}…"
    }

    private fun fontFileValue(file: File): String {
        return "$FontFilePrefix${file.absolutePath}"
    }

    private fun typefaceFromFontFileValue(value: String): Typeface? {
        return runCatching {
            val file = File(value.removePrefix(FontFilePrefix))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Product decision: custom typography must not turn a valid rare Han candidate
                // into a blank chip. One fallback family also keeps Paint measurement aligned
                // with the Typeface used by candidate TextViews.
                val family = FontFamily.Builder(Font.Builder(file).build()).build()
                Typeface.CustomFallbackBuilder(family)
                    .setSystemFallback("sans-serif")
                    .build()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Typeface.Builder(file)
                    .setFallback("sans-serif")
                    .build()
            } else {
                Typeface.createFromFile(file)
            }
        }.getOrNull()
    }
}
